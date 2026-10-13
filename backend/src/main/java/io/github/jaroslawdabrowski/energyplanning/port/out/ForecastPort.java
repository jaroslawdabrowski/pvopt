package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ExpensivePeriod;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionProfile;

public interface ForecastPort {

    /** Expected PV for each hour of a period (today and tomorrow are available). */
    ProductionProfile productionProfile(ExpensivePeriod period, ForecastSettings settings);
}
