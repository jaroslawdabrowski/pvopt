package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;

public record TouScheduleSlotResponse(
        int slotIndex,
        String startTime,
        String endTime,
        int powerWatts,
        int targetSocPercent,
        boolean gridChargeEnabled,
        String governedBy) {

    static TouScheduleSlotResponse from(TouScheduleSlot slot) {
        return new TouScheduleSlotResponse(
                slot.slotIndex(),
                slot.startTime().toString(),
                slot.endTime().toString(),
                slot.powerWatts(),
                slot.targetSocPercent(),
                slot.gridChargeEnabled(),
                slot.governedBy() != null ? slot.governedBy().name() : null);
    }
}
