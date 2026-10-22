package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ExpensivePeriod;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionProfile;

public interface ForecastPort {

    /** Expected PV for each hour of a period (today and tomorrow are available). */
    ProductionProfile productionProfile(ExpensivePeriod period, ForecastSettings settings);

    /**
     * Which forecast model produced the numbers - recorded with each decision and bumped when
     * the source changes, so the forecast calibration never learns one source's errors and
     * applies them to another's (1 = Forecast.Solar, 2 = Open-Meteo).
     */
    int modelVersion();
}
