package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;

/**
 * Inbound port: runs the full planning cycle (read SOC, fetch forecast, decide,
 * write the schedule to the inverter, record it in history) and returns the
 * decision made. Invoked both by the hourly scheduler and manually from the UI.
 */
public interface PlanEnergyUseCase {

    ChargeDecision planAndApply();
}
