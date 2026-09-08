package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;

/**
 * Inbound port: decides and applies the overnight (22:00-06:00) grid-charge schedule,
 * based on tomorrow's forecast vs. estimated daily consumption. Invoked both by the
 * scheduler (once daily, shortly before the window starts) and manually from the UI.
 */
public interface PlanOvernightChargeUseCase {

    ChargeDecision planOvernightCharge();
}
