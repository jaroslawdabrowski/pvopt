package io.github.jaroslawdabrowski.energyplanning.domain;

import java.util.List;

/**
 * The house's power flows right now, as the inverter measures them, in watts. Signs follow the
 * house's point of view: {@code batteryWatts} is positive while the battery discharges (feeds the
 * house) and negative while it charges; {@code gridWatts} is positive while buying from the grid and
 * negative while selling to it. They roughly balance:
 * {@code pv + battery + grid = load} (give or take the inverter's own losses).
 *
 * @param pvInputWatts production per PV input of the inverter (MPPT 1, 2, ...)
 */
public record LivePower(
        List<Integer> pvInputWatts,
        int loadWatts,
        int batteryWatts,
        int gridWatts,
        int batterySocPercent) {

    public LivePower {
        pvInputWatts = List.copyOf(pvInputWatts);
    }

    public int pvWatts() {
        return pvInputWatts.stream().mapToInt(Integer::intValue).sum();
    }
}
