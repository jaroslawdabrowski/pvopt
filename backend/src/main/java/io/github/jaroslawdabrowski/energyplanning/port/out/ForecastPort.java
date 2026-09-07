package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ProductionForecast;

import java.time.LocalDate;

public interface ForecastPort {

    ProductionForecast forecastFor(LocalDate date);
}
