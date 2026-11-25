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
 * Whenever the period needs anything from the battery, the battery is filled from the
 * cheap grid - only room for the PV surplus the forecast expects is left free
 * ({@link PeriodBalance#surplusKwh()}, counted at {@link #SOLAR_ROOM_SHARE}). The target used
 * to be just the reserve the forecast wouldn't cover, but in November that left the battery
 * at 20% by 09:00 on every overcast morning the forecast had been too sunny for, while the
 * afternoon window charged to 100% anyway - so whatever is left at 13:00 isn't wasted, it
 * only saves that afternoon charge, at the same price. The two mistakes don't cost the same:
 * too little in the battery means buying at the expensive rate (~0.6 zł/kWh more), too
 * little room means exporting sun at the market price instead of saving a cheap kWh
 * (~0.15-0.2 zł/kWh) - hence only part of the forecast surplus is kept free. The reserve
 * (largest running deficit) stays a floor under the target. With an unknown battery
 * capacity neither can be turned into a SOC, so the target is {@code fullSoc}.
 *
 * The decision doesn't depend on the SOC at decision time (except the safety-minimum
 * case): it's "grid charge on, target X", and the inverter applies it during the window -
 * topping up only if the battery is below X by then, otherwise holding it there so
 * cheap-hour load runs on the cheap grid instead of draining what the expensive hours
 * after the window need.
 */
public class ChargeDecisionPolicy {

    /** The share of the forecast PV surplus kept free in the battery - forecasts run sunny, a full battery is worth more. */
    static final double SOLAR_ROOM_SHARE = 0.6;

    public ChargeDecision decide(
            Instant now,
            ChargeWindow window,
            BatteryStatus battery,
            PeriodBalance balance,
            PlanningPolicyConfig config) {

        double solarRoomKwh = balance.surplusKwh() * SOLAR_ROOM_SHARE;
        int targetSoc = targetSocFor(balance.reserveKwh(), solarRoomKwh, config);

        if (balance.batteryNeedKwh() == 0 || targetSoc <= config.minSocPercent()) {
            if (battery.isBelow(config.minSocPercent())) {
                return new ChargeDecision(
                        now,
                        window,
                        true,
                        config.minSocPercent(),
                        DecisionReason.BELOW_MIN_SOC,
                        Map.of("soc", (double) battery.socPercent(), "minSoc", (double) config.minSocPercent()));
            }
            return noCharge(now, window, DecisionReason.FORECAST_SUFFICIENT,
                    Map.of("forecastKwh", tenths(balance.productionKwh()), "requiredKwh", tenths(balance.consumptionKwh())),
                    config.minSocPercent());
        }

        // Grid charge enabled with the target, whatever the battery holds right now: the inverter
        // compares SOC with the target itself, *during* the window - it tops up only if the battery
        // is below it by then, and otherwise just keeps it from going below. Comparing with the SOC
        // at decision time would be wrong for any decision made ahead of the window (a manual run
        // at 14:00 sees a battery that will be much emptier by 22:00).
        return new ChargeDecision(
                now,
                window,
                true,
                targetSoc,
                DecisionReason.FILL_LEAVING_SOLAR_ROOM,
                Map.of("forecastKwh", tenths(balance.productionKwh()), "requiredKwh", tenths(balance.consumptionKwh()),
                        "reserveKwh", tenths(balance.reserveKwh()), "solarRoomKwh", tenths(solarRoomKwh),
                        "targetSoc", (double) targetSoc, "soc", (double) battery.socPercent()));
    }

    /**
     * {@code fullSoc} less the room for the sun (rounded down to a whole percent), never under
     * {@code minSoc} plus the reserve (rounded up), capped at {@code fullSoc}.
     */
    static int targetSocFor(double reserveKwh, double solarRoomKwh, PlanningPolicyConfig config) {
        if (!config.knowsCapacity()) {
            return config.fullSocPercent();
        }
        int roomPercent = (int) Math.floor(solarRoomKwh / config.batteryCapacityKwh() * 100 + 1e-9);
        int reservePercent = reserveKwh <= 0 ? 0 : (int) Math.ceil(reserveKwh / config.batteryCapacityKwh() * 100 - 1e-9);
        int target = Math.max(config.fullSocPercent() - roomPercent, config.minSocPercent() + reservePercent);
        return Math.max(config.minSocPercent(), Math.min(config.fullSocPercent(), target));
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
