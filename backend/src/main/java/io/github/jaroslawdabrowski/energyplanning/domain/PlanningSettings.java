package io.github.jaroslawdabrowski.energyplanning.domain;

import java.util.List;

/**
 * SOC thresholds, consumption estimates and tariff windows - everything
 * {@link ChargeDecisionPolicy} and the dashboard's "tariff now" display need, supplied
 * by the user via the settings panel instead of static configuration.
 *
 * @param minSocPercent                     absolute minimum SOC - below it we always charge from the grid
 * @param fullSocPercent                    the highest SOC a grid charge may target
 * @param batteryCapacityKwh                usable battery capacity - turns a missing amount of energy into
 *                                          a target SOC; {@code 0} means unknown (settings saved before this
 *                                          field existed), in which case a grid charge always goes to
 *                                          {@code fullSocPercent}
 * @param dailyConsumptionEstimateKwh       estimated total daily consumption, used by the overnight decision
 * @param afternoonConsumptionEstimateKwh   estimated consumption for the rest of the day, used by the afternoon decision
 * @param forecastSafetyMarginRatio         multiplier applied to the daily consumption estimate for the overnight decision
 * @param cheapTariffWindows                cheap-tariff windows in "HH:mm-HH:mm" format, e.g. "22:00-06:00"
 */
public record PlanningSettings(
        int minSocPercent,
        int fullSocPercent,
        double batteryCapacityKwh,
        double dailyConsumptionEstimateKwh,
        double afternoonConsumptionEstimateKwh,
        double forecastSafetyMarginRatio,
        List<String> cheapTariffWindows) {

    public PlanningSettings {
        if (minSocPercent < 0 || minSocPercent > 100 || fullSocPercent < 0 || fullSocPercent > 100) {
            throw new IllegalArgumentException("SOC thresholds must be between 0 and 100");
        }
        if (minSocPercent > fullSocPercent) {
            throw new IllegalArgumentException("minSocPercent cannot be greater than fullSocPercent");
        }
        if (batteryCapacityKwh < 0) {
            throw new IllegalArgumentException("batteryCapacityKwh cannot be negative, was " + batteryCapacityKwh);
        }
        if (dailyConsumptionEstimateKwh < 0 || afternoonConsumptionEstimateKwh < 0) {
            throw new IllegalArgumentException("consumption estimates cannot be negative");
        }
        if (forecastSafetyMarginRatio <= 0) {
            throw new IllegalArgumentException("forecastSafetyMarginRatio must be positive, was " + forecastSafetyMarginRatio);
        }
        if (cheapTariffWindows == null || cheapTariffWindows.isEmpty()) {
            throw new IllegalArgumentException("cheapTariffWindows must not be empty");
        }
        cheapTariffWindows = List.copyOf(cheapTariffWindows);
    }
}
