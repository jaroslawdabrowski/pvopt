package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Connection parameters for the Deye/Solarman logger and the Modbus register map.
 * {@code batterySocRegister} is confirmed against real hardware (see
 * {@code RegisterScannerManualTest}); the grid-charge registers are still TODO -
 * register addresses differ between Deye inverter models/firmware.
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

    /** TODO: grid-charge enable/disable register address - verify against hardware. */
    @WithDefault("145")
    int gridChargeEnableRegister();

    /** TODO: grid-charge target SOC register address - verify against hardware. */
    @WithDefault("146")
    int gridChargeTargetSocRegister();

    @WithDefault("5000")
    int socketTimeoutMillis();
}
