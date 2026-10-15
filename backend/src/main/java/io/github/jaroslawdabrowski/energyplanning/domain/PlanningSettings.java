package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
        List<String> cheapTariffWindows,
        LocalDateTime consumptionAfterOvernightChangedAt,
        LocalDateTime consumptionAfterAfternoonChangedAt) {

    /** Settings as entered - when each estimate last changed is filled in on save ({@link #withChangesSince}). */
    public PlanningSettings(int minSocPercent, int fullSocPercent, double batteryCapacityKwh,
            double consumptionAfterOvernightKwh, double consumptionAfterAfternoonKwh, double forecastSafetyMarginRatio,
            List<String> cheapTariffWindows) {
        this(minSocPercent, fullSocPercent, batteryCapacityKwh, consumptionAfterOvernightKwh,
                consumptionAfterAfternoonKwh, forecastSafetyMarginRatio, cheapTariffWindows, null, null);
    }

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

    /**
     * Carries over when each consumption estimate last changed: a value that differs from
     * {@code previous} changed {@code now}, an unchanged one keeps its old date. Without
     * previous settings nothing is marked - the first save isn't a change in the house.
     */
    public PlanningSettings withChangesSince(PlanningSettings previous, LocalDateTime now) {
        if (previous == null) {
            return this;
        }
        return new PlanningSettings(minSocPercent, fullSocPercent, batteryCapacityKwh, consumptionAfterOvernightKwh,
                consumptionAfterAfternoonKwh, forecastSafetyMarginRatio, cheapTariffWindows,
                consumptionAfterOvernightKwh != previous.consumptionAfterOvernightKwh
                        ? now : previous.consumptionAfterOvernightChangedAt,
                consumptionAfterAfternoonKwh != previous.consumptionAfterAfternoonKwh
                        ? now : previous.consumptionAfterAfternoonChangedAt);
    }

    /** When the user last changed this window's estimate - a sign the house uses energy differently since. */
    public Optional<LocalDateTime> consumptionChangedAt(ChargeWindow window) {
        return Optional.ofNullable(switch (window) {
            case OVERNIGHT -> consumptionAfterOvernightChangedAt;
            case AFTERNOON -> consumptionAfterAfternoonChangedAt;
        });
    }

    /**
     * Measured periods count from here on: the last {@code lookBack}, but never from before the
     * estimate was last changed - when the heating goes on in autumn, the summer's lower
     * consumption shouldn't keep pulling the learned figure down for two weeks.
     */
    public LocalDateTime learnFrom(ChargeWindow window, LocalDateTime now, Duration lookBack) {
        var from = now.minus(lookBack);
        return consumptionChangedAt(window).filter(changed -> changed.isAfter(from)).orElse(from);
    }

    /** The consumption estimate for the expensive period a given decision has to cover. */
    public double consumptionAfter(ChargeWindow window) {
        return switch (window) {
            case OVERNIGHT -> consumptionAfterOvernightKwh;
            case AFTERNOON -> consumptionAfterAfternoonKwh;
        };
    }
}
