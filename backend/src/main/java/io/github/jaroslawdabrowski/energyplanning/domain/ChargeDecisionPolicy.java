package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Decision engine: whether it's worth enabling grid charging for a given window, and to
 * what SOC.
 *
 * Pure business logic - no framework annotations, no IO. All inputs (including
 * "now", and how much forecast is "required") are passed as arguments, so the
 * result is fully deterministic and testable without mocks. Called once per
 * {@link ChargeWindow} (overnight, afternoon) with different forecast/required
 * inputs - the decision rules themselves don't vary by window.
 *
 * The target is sized to the energy the forecast won't cover: the battery should hold
 * {@code minSoc} plus that shortfall, never more (capped at {@code fullSoc}). Charging
 * further from the grid only fills the room tomorrow's sun would have used - under
 * net-billing that surplus is then exported for a fraction of what it cost to buy. With
 * an unknown battery capacity the shortfall can't be turned into a SOC, so a grid charge
 * falls back to {@code fullSoc}.
 *
 * The decision doesn't depend on the SOC at decision time (except the safety-minimum
 * case): a shortfall always means "grid charge on, target = reserve", and the inverter
 * applies it during the window - topping up only if the battery is below the reserve by
 * then, otherwise holding it there so cheap-hour load runs on the cheap grid instead of
 * draining what the expensive hours after the window need.
 */
public class ChargeDecisionPolicy {

    public ChargeDecision decide(
            Instant now,
            ChargeWindow window,
            BatteryStatus battery,
            ProductionForecast forecast,
            double requiredKwh,
            PlanningPolicyConfig config) {

        double shortfallKwh = Math.max(0, requiredKwh - forecast.expectedKwh());
        int targetSoc = targetSocFor(shortfallKwh, config);

        if (battery.isBelow(config.minSocPercent()) && targetSoc == config.minSocPercent()) {
            return new ChargeDecision(
                    now,
                    window,
                    true,
                    config.minSocPercent(),
                    DecisionReason.BELOW_MIN_SOC,
                    Map.of("soc", (double) battery.socPercent(), "minSoc", (double) config.minSocPercent()));
        }

        if (shortfallKwh == 0) {
            return noCharge(now, window, DecisionReason.FORECAST_SUFFICIENT,
                    Map.of("forecastKwh", tenths(forecast.expectedKwh()), "requiredKwh", tenths(requiredKwh)),
                    config.minSocPercent());
        }

        // Grid charge enabled with the reserve as its target, whatever the battery holds right
        // now: the inverter compares SOC with the target itself, *during* the window - it tops
        // up only if the battery has drained below the reserve by then, and otherwise just keeps
        // it from going below. Comparing with the SOC at decision time would be wrong for any
        // decision made ahead of the window (a manual run at 14:00 sees a battery that will be
        // much emptier by 22:00).
        return new ChargeDecision(
                now,
                window,
                true,
                targetSoc,
                DecisionReason.FORECAST_INSUFFICIENT,
                Map.of("forecastKwh", tenths(forecast.expectedKwh()), "requiredKwh", tenths(requiredKwh),
                        "targetSoc", (double) targetSoc, "soc", (double) battery.socPercent()));
    }

    /** {@code minSoc} plus the shortfall as a share of the battery, rounded up, capped at {@code fullSoc}. */
    static int targetSocFor(double shortfallKwh, PlanningPolicyConfig config) {
        if (shortfallKwh <= 0) {
            return config.minSocPercent();
        }
        if (!config.knowsCapacity()) {
            return config.fullSocPercent();
        }
        int extraPercent = (int) Math.ceil(shortfallKwh / config.batteryCapacityKwh() * 100 - 1e-9);
        return Math.min(config.fullSocPercent(), config.minSocPercent() + extraPercent);
    }

    /** Reason params are shown to people - a tenth of a kWh is as precise as a forecast gets. */
    private static double tenths(double kwh) {
        return Math.round(kwh * 10) / 10.0;
    }

    private ChargeDecision noCharge(Instant now, ChargeWindow window, DecisionReason reason,
            Map<String, Double> params, int minSocPercent) {
        // The inverter's target-SOC register is a floor it will pull from the grid to
        // maintain, enforced regardless of the grid-charge-enable flag - confirmed by
        // observation: writing the battery's current SOC here (e.g. 70%) meant the
        // inverter topped up from the grid overnight the moment SOC dropped below 70%,
        // even with grid-charge OFF. So the floor is never "wherever the battery happened
        // to be": it's the safety minimum when nothing needs holding, or the computed
        // reserve - which is exactly what a grid charge targets anyway.
        return new ChargeDecision(now, window, false, minSocPercent, reason, params);
    }
}
