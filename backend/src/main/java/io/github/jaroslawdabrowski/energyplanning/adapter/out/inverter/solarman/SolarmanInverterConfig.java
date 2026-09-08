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
