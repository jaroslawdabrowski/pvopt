package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;

import java.time.LocalTime;
import java.util.List;

/** Takes a snapshot of the inverter's energy counters at a cheap window boundary. */
public interface RecordEnergySnapshotUseCase {

    /** Snapshot now, and record the outcome of any expensive period that ends now. */
    void recordSnapshot();

    /** When snapshots should be taken: every cheap window start and end. */
    List<LocalTime> snapshotTimes(ProcessSettings settings);
}
