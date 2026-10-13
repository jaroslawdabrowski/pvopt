package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.EnergySnapshot;
import io.github.jaroslawdabrowski.energyplanning.domain.PeriodOutcome;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Measured energy: counter snapshots at window boundaries, and the per-period outcomes derived from them. */
public interface EnergyRecordPort {

    void saveSnapshot(EnergySnapshot snapshot);

    /** The snapshot closest to {@code at}, if one was taken within {@code tolerance} of it. */
    Optional<EnergySnapshot> findSnapshotNear(LocalDateTime at, Duration tolerance);

    /** Saves an outcome, replacing one already recorded for the same window and period start. */
    void saveOutcome(PeriodOutcome outcome);

    /** Outcomes of a window's periods that started at or after {@code since}, oldest first. */
    List<PeriodOutcome> findOutcomes(ChargeWindow window, LocalDateTime since);

    /** Every outcome whose period started at or after {@code since}, oldest first. */
    List<PeriodOutcome> findOutcomesSince(LocalDateTime since);
}
