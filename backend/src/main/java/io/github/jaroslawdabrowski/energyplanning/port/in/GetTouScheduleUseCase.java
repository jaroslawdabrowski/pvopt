package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;

import java.util.List;

/** Inbound port: read-only view of the inverter's current 6-slot Time-of-Use schedule. */
public interface GetTouScheduleUseCase {

    List<TouScheduleSlot> currentTouSchedule();
}
