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

    /** Estimated total daily consumption, used by the overnight decision (tomorrow's forecast vs. this). */
    @WithDefault("16.0")
    double dailyConsumptionEstimateKwh();

    /**
     * Estimated consumption for the rest of the day from the afternoon decision point (~13:00) onward,
     * used by the afternoon top-up decision (today's remaining forecast vs. this).
     * TODO: tune to actual afternoon/evening usage once observed.
     */
    @WithDefault("8.0")
    double afternoonConsumptionEstimateKwh();

    @WithDefault("1.2")
    double forecastSafetyMarginRatio();

    /**
     * Cheap-tariff windows in "HH:mm-HH:mm" format, e.g. "22:00-06:00". Informational only
     * (drives the dashboard's "tariff now" display) - the planning jobs themselves are
     * time-triggered, not tariff-window-triggered.
     */
    @WithDefault("22:00-06:00,13:00-15:00")
    List<String> cheapTariffWindows();
}
