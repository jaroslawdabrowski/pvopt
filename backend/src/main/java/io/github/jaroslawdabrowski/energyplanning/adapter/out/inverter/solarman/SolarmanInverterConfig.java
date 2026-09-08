package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Connection parameters for the Deye/Solarman logger and the Modbus register map.
 * {@code batterySocRegister} and the TOU (Time-of-Use) register bases are confirmed
 * against real hardware (see {@code RegisterScannerManualTest}): the inverter has 6
 * TOU slots, one register per slot per field, at {@code touXxxBaseRegister + slotIndex}.
 */
@ConfigMapping(prefix = "pvopt.inverter.solarman")
public interface SolarmanInverterConfig {

    String host();

    @WithDefault("8899")
    int port();

    /** Logger serial number (not the inverter's!), required for the Solarman V5 frame. */
    long loggerSerial();

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
