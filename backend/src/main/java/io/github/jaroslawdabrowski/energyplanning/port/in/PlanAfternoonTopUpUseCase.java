package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;

/**
 * Inbound port: decides and applies the short afternoon (13:00-15:00) grid-charge
 * top-up, based on the rest of today's forecast vs. estimated remaining consumption.
 * Invoked both by the scheduler (once daily, shortly before the window starts) and
 * manually from the UI.
 */
public interface PlanAfternoonTopUpUseCase {

    ChargeDecision planAfternoonTopUp();
}
