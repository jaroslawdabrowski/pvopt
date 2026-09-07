package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;

/**
 * Port for talking to the inverter. The adapter implementation knows the protocol
 * (Solarman V5/Modbus, registers, etc.) - only domain concepts are visible here.
 */
public interface InverterPort {

    BatteryStatus readBatteryStatus();

    void applyChargeSchedule(ChargeSchedule schedule);
}
