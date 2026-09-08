package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.InverterConnectionSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;

import java.util.List;

/**
 * Port for talking to the inverter. The adapter implementation knows the protocol
 * (Solarman V5/Modbus, registers, etc.) - only domain concepts are visible here.
 * The connection (host/port/logger serial) is supplied per call rather than injected
 * statically, since it now comes from user-editable settings, not application.properties.
 */
public interface InverterPort {

    BatteryStatus readBatteryStatus(InverterConnectionSettings connection);

    void applyChargeSchedule(ChargeSchedule schedule, InverterConnectionSettings connection);

    /** Read-only view of the inverter's current 6-slot Time-of-Use schedule, for display. */
    List<TouScheduleSlot> readTouSchedule(InverterConnectionSettings connection);
}
