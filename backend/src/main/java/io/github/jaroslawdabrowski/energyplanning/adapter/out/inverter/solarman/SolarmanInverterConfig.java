package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * The Modbus register map and protocol-level knobs for the Deye/Solarman logger - all
 * static, hardware-protocol constants confirmed against real hardware (see
 * {@code RegisterScannerManualTest}), unlike the connection itself (host/port/logger
 * serial), which is now user-editable via the settings panel (see
 * {@code InverterConnectionSettings}) rather than configured here. The inverter has 6
 * TOU slots, one register per slot per field, at {@code touXxxBaseRegister + slotIndex}.
 */
@ConfigMapping(prefix = "pvopt.inverter.solarman")
public interface SolarmanInverterConfig {

    @WithDefault("1")
    int modbusSlaveAddress();

    /** Battery SOC (%) holding register - confirmed against real hardware (raw value = percent, no scaling). */
    @WithDefault("588")
    int batterySocRegister();

    /**
     * Base register for the 6 TOU slot start times (HHmm, e.g. 1700 = 17:00). Slot N's
     * time lives at {@code touTimeBaseRegister + N}. Confirmed against real hardware.
     * Only the one-off {@code TouSlotSetupManualTest} writes these.
     */
    @WithDefault("148")
    int touTimeBaseRegister();

    /**
     * Base register for the 6 TOU slots' grid-charge power cap (Watts). Slot N's cap is
     * {@code touPowerBaseRegister + N}. A static setting written once by
     * {@code TouSlotSetupManualTest} (see {@code TouSlots.powerWattsFor}) - not part of
     * the daily decision.
     */
    @WithDefault("154")
    int touPowerBaseRegister();

    /** Base register for the 6 TOU slots' target SOC (%). Slot N's target is {@code touBattTargetBaseRegister + N}. */
    @WithDefault("166")
    int touBattTargetBaseRegister();

    /** Base register for the 6 TOU slots' grid-charge enable flag (0/1). Slot N's flag is {@code touGridChargeEnableBaseRegister + N}. */
    @WithDefault("172")
    int touGridChargeEnableBaseRegister();

    /*
     * Daily energy counters (0.1 kWh units, reset at the inverter's midnight) - confirmed against
     * real hardware: read at 13:51 they balanced exactly (PV 5.4 + bought 6.0 + discharged 2.9 =
     * load 8.6 + sold 0.5 + charged 5.2). All six sit in 514-529, read as one block.
     */

    /** Today's battery charge, in 0.1 kWh. */
    @WithDefault("514")
    int dailyBatteryChargeRegister();

    /** Today's battery discharge, in 0.1 kWh. */
    @WithDefault("515")
    int dailyBatteryDischargeRegister();

    /** Today's energy bought from the grid, in 0.1 kWh. */
    @WithDefault("520")
    int dailyGridImportRegister();

    /** Today's energy sold to the grid, in 0.1 kWh. */
    @WithDefault("521")
    int dailyGridExportRegister();

    /** Today's house consumption, in 0.1 kWh. */
    @WithDefault("526")
    int dailyLoadRegister();

    /** Today's PV production, in 0.1 kWh. */
    @WithDefault("529")
    int dailyPvRegister();

    /**
     * Safety switch: while false (the default), any Modbus write (including the TOU slot
     * setup) only logs what it *would* write instead of actually sending it. Keep this
     * false until the register map above has been verified end-to-end against real
     * hardware and you're deliberately ready to let the scheduler write unattended.
     */
    @WithDefault("false")
    boolean writeEnabled();

    @WithDefault("5000")
    int socketTimeoutMillis();
}
