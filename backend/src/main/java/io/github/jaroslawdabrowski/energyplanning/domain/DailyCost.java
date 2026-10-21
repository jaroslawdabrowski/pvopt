package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalDate;

/**
 * One day's electricity in money. {@code baselineCost} is what the same consumption would
 * have cost bought entirely from the grid (no PV, no battery) - {@link #savings()} is the
 * difference to what was actually paid net of the export credit.
 *
 * @param coveredHours     hours of the day the counter snapshots account for (24 when complete)
 * @param approximate      some hours were spread across a gap between snapshots rather than measured
 * @param exportPriceGaps  some exported energy had no market price yet and was valued at 0
 */
public record DailyCost(
        LocalDate date,
        double importCheapKwh,
        double importExpensiveKwh,
        double importCost,
        double exportKwh,
        double exportValue,
        double loadKwh,
        double baselineCost,
        double coveredHours,
        boolean approximate,
        boolean exportPriceGaps) {

    public double netCost() {
        return importCost - exportValue;
    }

    public double savings() {
        return baselineCost - netCost();
    }

    /**
     * At least 23 hours accounted for. The day's totals are exact whenever the last snapshot is
     * late enough (the counters run from midnight); {@link #approximate} only means the
     * cheap/expensive split across a gap was estimated.
     */
    public boolean complete() {
        return coveredHours >= 23;
    }
}
