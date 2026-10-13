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
 * @param consumptionAfterOvernightKwh      estimated consumption in the expensive hours after the overnight window
 *                                          (G12: 06:00-13:00) - what the overnight charge has to cover
 * @param consumptionAfterAfternoonKwh      estimated consumption in the expensive hours after the afternoon window
 *                                          (G12: 15:00-22:00) - what the afternoon charge has to cover
 * @param forecastSafetyMarginRatio         multiplier applied to both consumption estimates
 * @param cheapTariffWindows                cheap-tariff windows in "HH:mm-HH:mm" format, e.g. "22:00-06:00"
 */
public record PlanningSettings(
        int minSocPercent,
        int fullSocPercent,
        double batteryCapacityKwh,
        double consumptionAfterOvernightKwh,
        double consumptionAfterAfternoonKwh,
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
        if (consumptionAfterOvernightKwh < 0 || consumptionAfterAfternoonKwh < 0) {
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

    /** The consumption estimate for the expensive period a given decision has to cover. */
    public double consumptionAfter(ChargeWindow window) {
        return switch (window) {
            case OVERNIGHT -> consumptionAfterOvernightKwh;
            case AFTERNOON -> consumptionAfterAfternoonKwh;
        };
    }
}
