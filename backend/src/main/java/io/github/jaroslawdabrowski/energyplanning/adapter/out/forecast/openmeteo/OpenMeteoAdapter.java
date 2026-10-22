package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.openmeteo;

import io.github.jaroslawdabrowski.energyplanning.domain.CompassDirection;
import io.github.jaroslawdabrowski.energyplanning.domain.ExpensivePeriod;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionProfile;
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The only adapter that knows about api.open-meteo.com - outward (through ForecastPort) only
 * the domain-level ProductionProfile (kWh per hour of a period) is exposed.
 *
 * For each plane with panels (one per {@link CompassDirection}, see {@link ForecastSettings})
 * it asks for the hourly irradiance on that plane's tilt and azimuth (global tilted
 * irradiance, from the weather models' cloud forecast) and turns it into energy:
 * {@code W/m² / 1000 x kWp x} {@link #PERFORMANCE_RATIO} - 1000 W/m² is the irradiance a
 * panel's kWp is rated at. Replaced Forecast.Solar, whose free tier gave nearly the same
 * morning figure on a clear day (9.5 kWh measured) and an overcast one (0.8 kWh); Open-Meteo's
 * day-ahead forecast had 10.3 and 0.3 (2026-10-21/22). Whatever the ratio misses (inverter,
 * temperature, shading) the planner's forecast calibration learns from measured PV.
 *
 * Open-Meteo refreshes its models about hourly and allows thousands of calls a day, but a
 * response is still cached for {@link #FRESH_FOR}, and when a call fails the last good one is
 * used for up to {@link #USABLE_FOR} - it covers today and tomorrow, so the 12:45 one is still
 * a sound forecast for the 21:45 decision. Only with nothing usable does the call fail - and
 * then the planner plans as if no sun were coming.
 */
@ApplicationScoped
public class OpenMeteoAdapter implements ForecastPort {

    /** Bumped from Forecast.Solar's 1 - see {@link ForecastPort#modelVersion()}. */
    static final int MODEL_VERSION = 2;
    static final double PERFORMANCE_RATIO = 0.85;
    static final Duration FRESH_FOR = Duration.ofHours(1);
    static final Duration USABLE_FOR = Duration.ofHours(24);
    private static final String HOURLY = "global_tilted_irradiance";
    private static final int FORECAST_DAYS = 2;

    private static final Logger LOG = Logger.getLogger(OpenMeteoAdapter.class);

    private final OpenMeteoRestClient restClient;
    private final Clock clock;
    /** Last good response per plane - keyed by everything that goes into the request. */
    private final Map<Plane, Fetched> cache = new ConcurrentHashMap<>();

    @Inject
    public OpenMeteoAdapter(@RestClient OpenMeteoRestClient restClient) {
        this(restClient, Clock.systemDefaultZone());
    }

    OpenMeteoAdapter(OpenMeteoRestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    private record Plane(double latitude, double longitude, int tilt, int azimuth) {
    }

    /** Irradiance (W/m²) by the hour each value ends at. */
    private record Fetched(Instant at, Map<LocalDateTime, Double> irradiance) {
    }

    @Override
    public int modelVersion() {
        return MODEL_VERSION;
    }

    @Override
    public ProductionProfile productionProfile(ExpensivePeriod period, ForecastSettings settings) {
        var slotHours = ProductionProfile.slotHoursFor(period);
        double[] kwh = new double[slotHours.size()];
        for (CompassDirection direction : activeDirections(settings)) {
            double kwp = settings.panelFor(direction).kwp();
            var irradiance = irradianceFor(settings, direction, period);
            LocalDateTime slotStart = period.from();
            for (int i = 0; i < slotHours.size(); i++) {
                LocalDateTime slotEnd = slotStart.plusMinutes(Math.round(slotHours.get(i) * 60));
                kwh[i] += kwhPerKwpBetween(irradiance, slotStart, slotEnd) * kwp;
                slotStart = slotEnd;
            }
        }
        return new ProductionProfile(period.from(), Arrays.stream(kwh).boxed().toList(), slotHours);
    }

    /**
     * Energy per installed kWp between two moments: each hour's mean irradiance (stamped at the
     * hour's end) for the part of that hour inside [from, to].
     */
    static double kwhPerKwpBetween(Map<LocalDateTime, Double> irradiance, LocalDateTime from, LocalDateTime to) {
        double total = 0;
        for (LocalDateTime hourStart = from.truncatedTo(ChronoUnit.HOURS); hourStart.isBefore(to);
                hourStart = hourStart.plusHours(1)) {
            LocalDateTime hourEnd = hourStart.plusHours(1);
            long minutesInside = Duration.between(max(hourStart, from), min(hourEnd, to)).toMinutes();
            Double wattsPerSquareMetre = irradiance.get(hourEnd);
            if (wattsPerSquareMetre == null) {
                throw new IllegalStateException("Open-Meteo has no irradiance for the hour ending " + hourEnd);
            }
            total += wattsPerSquareMetre / 1000.0 * PERFORMANCE_RATIO * minutesInside / 60.0;
        }
        return total;
    }

    /** The plane's irradiance covering the period - from cache, the API, or a recent good response. */
    private Map<LocalDateTime, Double> irradianceFor(ForecastSettings settings, CompassDirection direction,
            ExpensivePeriod period) {
        var plane = new Plane(settings.latitude(), settings.longitude(),
                settings.panelFor(direction).declinationDegrees(), direction.azimuthDegrees());
        Instant now = clock.instant();
        Fetched cached = cache.get(plane);
        if (cached != null && age(cached, now).compareTo(FRESH_FOR) < 0 && covers(cached.irradiance(), period)) {
            return cached.irradiance();
        }
        try {
            var response = restClient.tiltedIrradiance(plane.latitude(), plane.longitude(), plane.tilt(),
                    plane.azimuth(), HOURLY, FORECAST_DAYS, clock.getZone().getId());
            var fetched = new Fetched(now, byHour(response));
            cache.put(plane, fetched);
            if (!covers(fetched.irradiance(), period)) {
                throw new IllegalStateException("Open-Meteo has no data for " + period.from() + " - " + period.to());
            }
            return fetched.irradiance();
        } catch (RuntimeException e) {
            if (cached != null && age(cached, now).compareTo(USABLE_FOR) < 0 && covers(cached.irradiance(), period)) {
                LOG.warnf("Open-Meteo call for %s failed (%s) - using the response from %s",
                        direction, e.getMessage(), cached.at());
                return cached.irradiance();
            }
            throw e;
        }
    }

    private static Map<LocalDateTime, Double> byHour(OpenMeteoResponse response) {
        var hourly = response.hourly();
        var result = new HashMap<LocalDateTime, Double>();
        for (int i = 0; i < hourly.time().size(); i++) {
            Double value = hourly.globalTiltedIrradiance().get(i);
            if (value != null) {
                result.put(LocalDateTime.parse(hourly.time().get(i)), value);
            }
        }
        return result;
    }

    /** Whether every hour of the period has a value - a missing one would otherwise read as no sun. */
    static boolean covers(Map<LocalDateTime, Double> irradiance, ExpensivePeriod period) {
        for (LocalDateTime hourEnd = period.from().truncatedTo(ChronoUnit.HOURS).plusHours(1);
                hourEnd.isBefore(period.to().plusHours(1)); hourEnd = hourEnd.plusHours(1)) {
            if (!irradiance.containsKey(hourEnd)) {
                return false;
            }
        }
        return true;
    }

    private static Duration age(Fetched fetched, Instant now) {
        return Duration.between(fetched.at(), now);
    }

    private static List<CompassDirection> activeDirections(ForecastSettings settings) {
        return Arrays.stream(CompassDirection.values())
                .filter(direction -> settings.panelFor(direction).hasPanel())
                .toList();
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
