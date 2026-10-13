package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import io.github.jaroslawdabrowski.energyplanning.domain.CompassDirection;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ExpensivePeriod;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionProfile;
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The only adapter that knows about api.forecast.solar - outward (through
 * ForecastPort) only the domain-level ProductionProfile (kWh per hour of a period) is exposed.
 * Only the hourly cumulative {@code watt_hours} series is used: the planner asks for
 * production within specific hours (the expensive period after a cheap window), not
 * whole-day totals.
 *
 * The installation can have up to 4 separate PV planes, one per {@link CompassDirection}
 * (see {@link ForecastSettings}) - every forecast sums one independent Forecast.Solar
 * call per direction that actually has a panel (kwp > 0); directions with no panel are
 * skipped entirely, not called with kwp=0.
 *
 * The free tier allows 12 calls an hour per IP, and every decision costs one call per
 * plane - a few manual runs and the limit is gone (it answers 429). So each plane's
 * response is cached for {@link #FRESH_FOR} (Forecast.Solar itself only refreshes about
 * hourly), and when a call fails anyway the last good response is used for up to
 * {@link #USABLE_FOR}: a response always covers today and tomorrow, so the 12:45 one is
 * still a sound forecast for the 21:45 decision. Only with nothing usable does the call
 * fail - and then the planner plans as if no sun were coming.
 */
@ApplicationScoped
public class ForecastSolarAdapter implements ForecastPort {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static final Duration FRESH_FOR = Duration.ofHours(1);
    static final Duration USABLE_FOR = Duration.ofHours(24);

    private static final Logger LOG = Logger.getLogger(ForecastSolarAdapter.class);

    private final ForecastSolarRestClient restClient;
    private final Clock clock;
    /** Last good response per plane - keyed by everything that goes into the request. */
    private final Map<Plane, Fetched> cache = new ConcurrentHashMap<>();

    @Inject
    public ForecastSolarAdapter(@RestClient ForecastSolarRestClient restClient) {
        this(restClient, Clock.systemDefaultZone());
    }

    ForecastSolarAdapter(ForecastSolarRestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    private record Plane(double latitude, double longitude, int declination, int azimuth, double kwp) {
    }

    private record Fetched(Instant at, Map<String, Long> wattHours) {
    }

    @Override
    public ProductionProfile productionProfile(ExpensivePeriod period, ForecastSettings settings) {
        var slotHours = ProductionProfile.slotHoursFor(period);
        double[] wattHours = new double[slotHours.size()];
        for (CompassDirection direction : activeDirections(settings)) {
            var series = wattHoursFor(settings, direction, period.from(), period.to());
            LocalDateTime slotStart = period.from();
            for (int i = 0; i < slotHours.size(); i++) {
                LocalDateTime slotEnd = slotStart.plusMinutes(Math.round(slotHours.get(i) * 60));
                wattHours[i] += wattHoursBetween(series, slotStart, slotEnd);
                slotStart = slotEnd;
            }
        }
        return new ProductionProfile(period.from(),
                Arrays.stream(wattHours).map(wh -> wh / 1000.0).boxed().toList(), slotHours);
    }

    /** The plane's cumulative series covering [from, to] - from cache, the API, or a recent good response. */
    private Map<String, Long> wattHoursFor(ForecastSettings settings, CompassDirection direction,
            LocalDateTime from, LocalDateTime to) {
        var panel = settings.panelFor(direction);
        var plane = new Plane(settings.latitude(), settings.longitude(), panel.declinationDegrees(),
                direction.azimuthDegrees(), panel.kwp());
        Instant now = clock.instant();
        Fetched cached = cache.get(plane);
        if (cached != null && age(cached, now).compareTo(FRESH_FOR) < 0 && covers(cached.wattHours(), from, to)) {
            return cached.wattHours();
        }
        try {
            var response = restClient.estimate(plane.latitude(), plane.longitude(), plane.declination(),
                    plane.azimuth(), plane.kwp());
            var fetched = new Fetched(now, response.result().wattHours());
            cache.put(plane, fetched);
            if (!covers(fetched.wattHours(), from, to)) {
                throw new IllegalStateException("Forecast.Solar has no data for " + from.toLocalDate()
                        + (to.toLocalDate().equals(from.toLocalDate()) ? "" : " - " + to.toLocalDate()));
            }
            return fetched.wattHours();
        } catch (RuntimeException e) {
            if (cached != null && age(cached, now).compareTo(USABLE_FOR) < 0 && covers(cached.wattHours(), from, to)) {
                LOG.warnf("Forecast.Solar call for %s failed (%s) - using the response from %s",
                        direction, e.getMessage(), cached.at());
                return cached.wattHours();
            }
            throw e;
        }
    }

    private static Duration age(Fetched fetched, Instant now) {
        return Duration.between(fetched.at(), now);
    }

    /**
     * Whether the series has data for every day [from, to] touches - a missing day would
     * otherwise silently read as 0 kWh (an old response doesn't reach that far).
     */
    static boolean covers(Map<String, Long> cumulativeWattHours, LocalDateTime from, LocalDateTime to) {
        var days = cumulativeWattHours.keySet().stream()
                .map(key -> LocalDateTime.parse(key, TIMESTAMP_FORMAT).toLocalDate())
                .collect(Collectors.toSet());
        for (LocalDate day = from.toLocalDate(); !day.isAfter(to.toLocalDate()); day = day.plusDays(1)) {
            if (!days.contains(day)) {
                return false;
            }
        }
        return true;
    }

    private List<CompassDirection> activeDirections(ForecastSettings settings) {
        return Arrays.stream(CompassDirection.values())
                .filter(direction -> settings.panelFor(direction).hasPanel())
                .toList();
    }

    /**
     * {@code watt_hours} is cumulative since midnight, per day - so production between two
     * moments is, for each day they touch, the cumulative value at the later moment minus the
     * value at the earlier one (taking the latest timestamp at or before each moment).
     */
    static long wattHoursBetween(Map<String, Long> cumulativeWattHours, LocalDateTime from, LocalDateTime to) {
        var series = cumulativeWattHours.entrySet().stream()
                .collect(Collectors.toMap(entry -> LocalDateTime.parse(entry.getKey(), TIMESTAMP_FORMAT),
                        Map.Entry::getValue, (a, b) -> a, TreeMap::new));
        long total = 0;
        for (LocalDate day = from.toLocalDate(); !day.isAfter(to.toLocalDate()); day = day.plusDays(1)) {
            LocalDateTime dayFrom = day.equals(from.toLocalDate()) ? from : day.atStartOfDay();
            LocalDateTime dayTo = day.equals(to.toLocalDate()) ? to : day.plusDays(1).atStartOfDay().minusNanos(1);
            total += Math.max(0, cumulativeAt(series, day, dayTo) - cumulativeAt(series, day, dayFrom));
        }
        return total;
    }

    private static long cumulativeAt(TreeMap<LocalDateTime, Long> series, LocalDate day, LocalDateTime moment) {
        var entry = series.floorEntry(moment);
        return entry != null && entry.getKey().toLocalDate().equals(day) ? entry.getValue() : 0L;
    }
}
