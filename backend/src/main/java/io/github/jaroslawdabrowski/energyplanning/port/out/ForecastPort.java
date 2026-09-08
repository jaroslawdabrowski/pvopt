package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionForecast;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ForecastPort {

    ProductionForecast forecastFor(LocalDate date, ForecastSettings settings);

    /** Expected remaining production from {@code now} until midnight of the same day. */
    ProductionForecast remainingToday(LocalDateTime now, ForecastSettings settings);
}
