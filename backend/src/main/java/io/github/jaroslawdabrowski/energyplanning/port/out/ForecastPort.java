package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionForecast;

import java.time.LocalDateTime;

public interface ForecastPort {

    /** Expected PV production between two local times (today and tomorrow are available). */
    ProductionForecast productionBetween(LocalDateTime from, LocalDateTime to, ForecastSettings settings);
}
