package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * The inverter's own energy counters since its midnight (they reset every day), in kWh.
 * They balance: {@code pv + gridImport + batteryDischarge = load + gridExport + batteryCharge}
 * (give or take conversion losses) - which is how they were confirmed on real hardware.
 */
public record DailyEnergyCounters(
        double pvKwh,
        double loadKwh,
        double gridImportKwh,
        double gridExportKwh,
        double batteryChargeKwh,
        double batteryDischargeKwh) {

    /** What accumulated between an earlier reading of the same day and this one. */
    public DailyEnergyCounters minus(DailyEnergyCounters earlier) {
        return new DailyEnergyCounters(
                Math.max(0, pvKwh - earlier.pvKwh),
                Math.max(0, loadKwh - earlier.loadKwh),
                Math.max(0, gridImportKwh - earlier.gridImportKwh),
                Math.max(0, gridExportKwh - earlier.gridExportKwh),
                Math.max(0, batteryChargeKwh - earlier.batteryChargeKwh),
                Math.max(0, batteryDischargeKwh - earlier.batteryDischargeKwh));
    }
}
