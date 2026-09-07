package io.github.jaroslawdabrowski.energyplanning.application;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

/**
 * Domain configuration for planning - SOC thresholds, estimated consumption, G12 tariff windows.
 * Loaded from application.properties (prefix "pvopt.planning").
 */
@ConfigMapping(prefix = "pvopt.planning")
public interface EnergyPlanningConfig {

    @WithDefault("20")
    int minSocPercent();

    @WithDefault("100")
    int fullSocPercent();

    @WithDefault("15.0")
    double dailyConsumptionEstimateKwh();

    @WithDefault("1.2")
    double forecastSafetyMarginRatio();

    /**
     * Cheap-tariff windows in "HH:mm-HH:mm" format, e.g. "22:00-06:00".
     */
    @WithDefault("22:00-06:00,13:00-15:00")
    List<String> cheapTariffWindows();
}
