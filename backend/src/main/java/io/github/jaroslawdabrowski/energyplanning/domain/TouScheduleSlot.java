package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalTime;

/**
 * A read-only view of one slot of the inverter's own Time-of-Use schedule - what's
 * actually programmed on the device right now, for display. {@code governedBy} is
 * {@code null} for slots neither daily decision touches (they're never grid-charge-enabled
 * by this app, though the device itself may still have arbitrary values in them).
 */
public record TouScheduleSlot(
        int slotIndex,
        LocalTime startTime,
        LocalTime endTime,
        int powerWatts,
        int targetSocPercent,
        boolean gridChargeEnabled,
        ChargeWindow governedBy) {
}
