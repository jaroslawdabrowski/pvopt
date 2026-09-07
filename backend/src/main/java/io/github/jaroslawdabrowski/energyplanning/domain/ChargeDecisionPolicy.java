package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Decision engine: whether it's worth enabling grid charging right now.
 *
 * Pure business logic - no framework annotations, no IO. All inputs (including
 * "now") are passed as arguments, so the result is fully deterministic and
 * testable without mocks.
 */
public class ChargeDecisionPolicy {

    public ChargeDecision decide(
            Instant now,
            BatteryStatus battery,
            TariffRate currentRate,
            ProductionForecast tomorrowForecast,
            PlanningPolicyConfig config) {

        if (currentRate != TariffRate.CHEAP) {
            return noCharge(now, battery, DecisionReason.OUTSIDE_CHEAP_WINDOW, Map.of());
        }

        if (battery.isAtOrAbove(config.fullSocPercent())) {
            return noCharge(now, battery, DecisionReason.ALREADY_AT_TARGET_SOC,
                    Map.of("targetSoc", (double) config.fullSocPercent()));
        }

        if (battery.isBelow(config.minSocPercent())) {
            return new ChargeDecision(
                    now,
                    true,
                    config.minSocPercent(),
                    DecisionReason.BELOW_MIN_SOC,
                    Map.of("soc", (double) battery.socPercent(), "minSoc", (double) config.minSocPercent()));
        }

        double requiredKwh = config.requiredForecastKwh();
        if (tomorrowForecast.coversAtLeast(requiredKwh)) {
            return noCharge(now, battery, DecisionReason.FORECAST_SUFFICIENT,
                    Map.of("forecastKwh", tomorrowForecast.expectedKwh(), "requiredKwh", requiredKwh));
        }

        return new ChargeDecision(
                now,
                true,
                config.fullSocPercent(),
                DecisionReason.FORECAST_INSUFFICIENT,
                Map.of("forecastKwh", tomorrowForecast.expectedKwh(), "requiredKwh", requiredKwh,
                        "targetSoc", (double) config.fullSocPercent()));
    }

    private ChargeDecision noCharge(Instant now, BatteryStatus battery, DecisionReason reason,
            Map<String, Double> params) {
        return new ChargeDecision(now, false, battery.socPercent(), reason, params);
    }
}
