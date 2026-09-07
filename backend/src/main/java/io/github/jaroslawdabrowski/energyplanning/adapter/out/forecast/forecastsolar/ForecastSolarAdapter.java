package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionForecast;
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * The only adapter that knows about api.forecast.solar - outward (through
 * ForecastPort) only the domain-level ProductionForecast (date + kWh) is exposed.
 */
@ApplicationScoped
public class ForecastSolarAdapter implements ForecastPort {

    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ForecastSolarRestClient restClient;
    private final ForecastSolarConfig config;

    public ForecastSolarAdapter(@RestClient ForecastSolarRestClient restClient, ForecastSolarConfig config) {
        this.restClient = restClient;
        this.config = config;
    }

    @Override
    public ProductionForecast forecastFor(LocalDate date) {
        var response = restClient.estimate(config.latitude(), config.longitude(), config.declination(),
                config.azimuth(), config.kwp());

        long wattHours = response.result().wattHoursDay().getOrDefault(date.format(DATE_KEY_FORMAT), 0L);
        return new ProductionForecast(date, wattHours / 1000.0);
    }
}
