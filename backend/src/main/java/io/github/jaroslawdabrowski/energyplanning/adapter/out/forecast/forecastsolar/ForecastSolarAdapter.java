package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionForecast;
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * The only adapter that knows about api.forecast.solar - outward (through
 * ForecastPort) only the domain-level ProductionForecast (date + kWh) is exposed.
 *
 * The installation has two separate PV planes (east/west), so every forecast is the
 * sum of two independent Forecast.Solar calls, one per plane (see ForecastSolarConfig).
 */
@ApplicationScoped
public class ForecastSolarAdapter implements ForecastPort {

    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ForecastSolarRestClient restClient;
    private final ForecastSolarConfig config;

    public ForecastSolarAdapter(@RestClient ForecastSolarRestClient restClient, ForecastSolarConfig config) {
        this.restClient = restClient;
        this.config = config;
    }

    @Override
    public ProductionForecast forecastFor(LocalDate date) {
        String dateKey = date.format(DATE_KEY_FORMAT);
        double wattHours = List.of(config.east(), config.west()).stream()
                .mapToLong(plane -> estimate(plane).result().wattHoursDay().getOrDefault(dateKey, 0L))
                .sum();
        return new ProductionForecast(date, wattHours / 1000.0);
    }

    @Override
    public ProductionForecast remainingToday(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        double remainingWattHours = List.of(config.east(), config.west()).stream()
                .mapToLong(plane -> remainingWattHoursForPlane(plane, today, now))
                .sum();
        return new ProductionForecast(today, remainingWattHours / 1000.0);
    }

    private long remainingWattHoursForPlane(ForecastSolarConfig.Plane plane, LocalDate today, LocalDateTime now) {
        var result = estimate(plane).result();
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

    private ForecastSolarEstimateResponse estimate(ForecastSolarConfig.Plane plane) {
        return restClient.estimate(config.latitude(), config.longitude(), plane.declination(), plane.azimuth(),
                plane.kwp());
    }
}
