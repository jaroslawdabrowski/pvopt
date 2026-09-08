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

/**
 * The only adapter that knows about api.forecast.solar - outward (through
 * ForecastPort) only the domain-level ProductionForecast (date + kWh) is exposed.
 *
 * The installation can have up to 4 separate PV planes, one per {@link CompassDirection}
 * (see {@link ForecastSettings}) - every forecast sums one independent Forecast.Solar
 * call per direction that actually has a panel (kwp > 0); directions with no panel are
 * skipped entirely, not called with kwp=0.
 */
@ApplicationScoped
public class ForecastSolarAdapter implements ForecastPort {

    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ForecastSolarRestClient restClient;

    public ForecastSolarAdapter(@RestClient ForecastSolarRestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ProductionForecast forecastFor(LocalDate date, ForecastSettings settings) {
        String dateKey = date.format(DATE_KEY_FORMAT);
        double wattHours = activeDirections(settings).stream()
                .mapToLong(direction -> estimate(settings, direction).result().wattHoursDay().getOrDefault(dateKey, 0L))
                .sum();
        return new ProductionForecast(date, wattHours / 1000.0);
    }

    @Override
    public ProductionForecast remainingToday(LocalDateTime now, ForecastSettings settings) {
        LocalDate today = now.toLocalDate();
        double remainingWattHours = activeDirections(settings).stream()
                .mapToLong(direction -> remainingWattHoursForDirection(settings, direction, today, now))
                .sum();
        return new ProductionForecast(today, remainingWattHours / 1000.0);
    }

    private List<CompassDirection> activeDirections(ForecastSettings settings) {
        return Arrays.stream(CompassDirection.values())
                .filter(direction -> settings.panelFor(direction).hasPanel())
                .toList();
    }

    private long remainingWattHoursForDirection(ForecastSettings settings, CompassDirection direction,
            LocalDate today, LocalDateTime now) {
        var result = estimate(settings, direction).result();
        long totalToday = result.wattHoursDay().getOrDefault(today.format(DATE_KEY_FORMAT), 0L);

        // watt_hours is cumulative-since-midnight per timestamp; the value at the latest
        // timestamp <= now is how much has already been produced today, so what's left
        // is the day's total minus that.
        long producedSoFar = result.wattHours().entrySet().stream()
                .map(entry -> Map.entry(LocalDateTime.parse(entry.getKey(), TIMESTAMP_FORMAT), entry.getValue()))
                .filter(entry -> !entry.getKey().toLocalDate().isAfter(today) && !entry.getKey().isAfter(now))
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(0L);

        return Math.max(0, totalToday - producedSoFar);
    }

    private ForecastSolarEstimateResponse estimate(ForecastSettings settings, CompassDirection direction) {
        var panel = settings.panelFor(direction);
        return restClient.estimate(settings.latitude(), settings.longitude(), panel.declinationDegrees(),
                direction.azimuthDegrees(), panel.kwp());
    }
}
