package io.github.jaroslawdabrowski.energyplanning.domain;

import java.util.List;

/**
 * Hour by hour, what an expensive period will take from the battery. Totals alone hide
 * timing: in the morning the house uses energy from 06:00 while the panels only catch up
 * around 09:00-10:00, so a period whose PV total almost covers its consumption can still
 * need a lot from the battery first - surplus later in the period comes back too late.
 *
 * {@code reserveKwh} is the largest running deficit (consumption minus PV, accumulated from
 * the period start): the energy the battery must hold at the start so it never runs out.
 * It's never less than the overall shortfall, and 0 when PV leads consumption throughout.
 *
 * {@code surplusKwh} is the mirror image, the largest running surplus: how far above its
 * starting level PV would push the battery - the room to leave free so that sun isn't
 * exported. {@code batteryNeedKwh} adds up every hour's consumption the PV of that same hour
 * doesn't cover: 0 means the period needs nothing from the battery at all.
 *
 * @param consumptionKwh   total expected consumption (margin included)
 * @param productionKwh    total expected PV
 * @param reserveKwh       the largest running deficit
 * @param peakHourOffset   hours from the period start to the end of the slot where that deficit peaks
 * @param surplusKwh       the largest running surplus
 * @param batteryNeedKwh   the sum of each hour's consumption over that hour's PV
 */
public record PeriodBalance(double consumptionKwh, double productionKwh, double reserveKwh, double peakHourOffset,
        double surplusKwh, double batteryNeedKwh) {

    /** Hour slots in order; both lists the same length (the last slot may be a partial hour). */
    public static PeriodBalance of(List<Double> consumptionPerSlot, List<Double> productionPerSlot,
            List<Double> slotHours) {
        if (consumptionPerSlot.size() != productionPerSlot.size() || slotHours.size() != productionPerSlot.size()) {
            throw new IllegalArgumentException("consumption, production and slot lengths must line up");
        }
        double running = 0;
        double peak = 0;
        double lowest = 0;
        double elapsed = 0;
        double peakAt = 0;
        double need = 0;
        for (int i = 0; i < consumptionPerSlot.size(); i++) {
            double net = consumptionPerSlot.get(i) - productionPerSlot.get(i);
            running += net;
            need += Math.max(0, net);
            elapsed += slotHours.get(i);
            if (running > peak) {
                peak = running;
                peakAt = elapsed;
            }
            lowest = Math.min(lowest, running);
        }
        double consumption = consumptionPerSlot.stream().mapToDouble(Double::doubleValue).sum();
        double production = productionPerSlot.stream().mapToDouble(Double::doubleValue).sum();
        return new PeriodBalance(consumption, production, peak, peakAt, -lowest, need);
    }

    /** The old whole-period view - for callers that only know totals. */
    public static PeriodBalance ofTotals(double consumptionKwh, double productionKwh) {
        return new PeriodBalance(consumptionKwh, productionKwh, Math.max(0, consumptionKwh - productionKwh), 0,
                Math.max(0, productionKwh - consumptionKwh), Math.max(0, consumptionKwh - productionKwh));
    }
}
