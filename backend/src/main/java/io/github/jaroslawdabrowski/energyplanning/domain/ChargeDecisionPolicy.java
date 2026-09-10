package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Decision engine: whether it's worth enabling grid charging for a given window.
 *
 * Pure business logic - no framework annotations, no IO. All inputs (including
 * "now", and how much forecast is "required") are passed as arguments, so the
 * result is fully deterministic and testable without mocks. Called once per
 * {@link ChargeWindow} (overnight, afternoon) with different forecast/required
 * inputs - the decision rules themselves don't vary by window.
 */
public class ChargeDecisionPolicy {

    public ChargeDecision decide(
            Instant now,
            ChargeWindow window,
            BatteryStatus battery,
            ProductionForecast forecast,
            double requiredKwh,
            PlanningPolicyConfig config) {

        if (battery.isAtOrAbove(config.fullSocPercent())) {
            return noCharge(now, window, DecisionReason.ALREADY_AT_TARGET_SOC,
                    Map.of("targetSoc", (double) config.fullSocPercent()), config.minSocPercent());
        }

        if (battery.isBelow(config.minSocPercent())) {
            return new ChargeDecision(
                    now,
                    window,
                    true,
                    config.minSocPercent(),
                    DecisionReason.BELOW_MIN_SOC,
                    Map.of("soc", (double) battery.socPercent(), "minSoc", (double) config.minSocPercent()));
        }

        if (forecast.coversAtLeast(requiredKwh)) {
            return noCharge(now, window, DecisionReason.FORECAST_SUFFICIENT,
                    Map.of("forecastKwh", forecast.expectedKwh(), "requiredKwh", requiredKwh), config.minSocPercent());
        }

        return new ChargeDecision(
                now,
                window,
                true,
                config.fullSocPercent(),
                DecisionReason.FORECAST_INSUFFICIENT,
                Map.of("forecastKwh", forecast.expectedKwh(), "requiredKwh", requiredKwh,
                        "targetSoc", (double) config.fullSocPercent()));
    }

    private ChargeDecision noCharge(Instant now, ChargeWindow window, DecisionReason reason,
            Map<String, Double> params, int minSocPercent) {
        // The inverter's target-SOC register is a floor it will pull from the grid to
        // maintain, enforced regardless of the grid-charge-enable flag - confirmed by
        // observation: writing the battery's current SOC here (e.g. 70%) meant the
        // inverter topped up from the grid overnight the moment SOC dropped below 70%,
        // even with grid-charge OFF. When we don't want any grid charging, the floor
        // must be the safety minimum, not wherever the battery happened to be.
        return new ChargeDecision(now, window, false, minSocPercent, reason, params);
    }
}
