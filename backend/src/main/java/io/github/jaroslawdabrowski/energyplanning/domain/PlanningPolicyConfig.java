package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Decision thresholds for the planning engine, supplied from configuration.
 *
 * @param minSocPercent               absolute minimum SOC - below it we always charge from the grid,
 *                                    regardless of forecast (power-supply safety net)
 * @param fullSocPercent              target SOC level for a full grid charge
 * @param dailyConsumptionEstimateKwh estimated daily household consumption (a fixed configurable value for now)
 * @param forecastSafetyMarginRatio   forecast safety margin, e.g. 1.2 = the forecast must cover 120% of
 *                                    estimated consumption to be considered sufficient on its own tomorrow
 */
public record PlanningPolicyConfig(
        int minSocPercent,
        int fullSocPercent,
        double dailyConsumptionEstimateKwh,
        double forecastSafetyMarginRatio) {

    public PlanningPolicyConfig {
        if (minSocPercent < 0 || minSocPercent > 100 || fullSocPercent < 0 || fullSocPercent > 100) {
            throw new IllegalArgumentException("SOC thresholds must be between 0 and 100");
        }
        if (minSocPercent > fullSocPercent) {
            throw new IllegalArgumentException("minSocPercent cannot be greater than fullSocPercent");
        }
        if (dailyConsumptionEstimateKwh < 0) {
            throw new IllegalArgumentException("dailyConsumptionEstimateKwh cannot be negative");
        }
        if (forecastSafetyMarginRatio <= 0) {
            throw new IllegalArgumentException("forecastSafetyMarginRatio must be positive");
        }
    }

    public double requiredForecastKwh() {
        return dailyConsumptionEstimateKwh * forecastSafetyMarginRatio;
    }
}
