package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import io.github.jaroslawdabrowski.energyplanning.domain.CompassDirection;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionForecast;
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The only adapter that knows about api.forecast.solar - outward (through
 * ForecastPort) only the domain-level ProductionForecast (date + kWh) is exposed.
 * Only the hourly cumulative {@code watt_hours} series is used: the planner asks for
 * production within specific hours (the expensive period after a cheap window), not
 * whole-day totals.
 *
 * The installation can have up to 4 separate PV planes, one per {@link CompassDirection}
 * (see {@link ForecastSettings}) - every forecast sums one independent Forecast.Solar
 * call per direction that actually has a panel (kwp > 0); directions with no panel are
 * skipped entirely, not called with kwp=0.
 */
@ApplicationScoped
public class ForecastSolarAdapter implements ForecastPort {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ForecastSolarRestClient restClient;

    public ForecastSolarAdapter(@RestClient ForecastSolarRestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ProductionForecast productionBetween(LocalDateTime from, LocalDateTime to, ForecastSettings settings) {
        double wattHours = activeDirections(settings).stream()
                .mapToLong(direction -> wattHoursBetween(estimate(settings, direction).result().wattHours(), from, to))
                .sum();
        return new ProductionForecast(from.toLocalDate(), wattHours / 1000.0);
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

    private ForecastSolarEstimateResponse estimate(ForecastSettings settings, CompassDirection direction) {
        var panel = settings.panelFor(direction);
        return restClient.estimate(settings.latitude(), settings.longitude(), panel.declinationDegrees(),
                direction.azimuthDegrees(), panel.kwp());
    }
}
