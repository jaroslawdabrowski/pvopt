package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalDateTime;

/**
 * What actually happened in the expensive period after a cheap window - measured, not
 * forecast. {@code gridImportKwh} is the number that says whether the decision before it was
 * right: ideally ~0, since every kWh bought here costs the expensive rate.
 */
public record PeriodOutcome(
        ChargeWindow window,
        LocalDateTime from,
        LocalDateTime to,
        double loadKwh,
        double pvKwh,
        double gridImportKwh,
        double gridExportKwh,
        int socAtStartPercent,
        int socAtEndPercent) {

    public static PeriodOutcome between(ChargeWindow window, EnergySnapshot start, EnergySnapshot end) {
        var delta = end.counters().minus(start.counters());
        return new PeriodOutcome(window, start.takenAt(), end.takenAt(), delta.loadKwh(), delta.pvKwh(),
                delta.gridImportKwh(), delta.gridExportKwh(), start.socPercent(), end.socPercent());
    }
}
