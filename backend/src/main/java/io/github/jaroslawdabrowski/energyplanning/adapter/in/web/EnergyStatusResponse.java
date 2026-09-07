package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.github.jaroslawdabrowski.energyplanning.port.in.GetEnergyStatusUseCase.EnergyStatus;

public record EnergyStatusResponse(int batterySocPercent, String tariffRate, ChargeDecisionResponse lastDecision) {

    static EnergyStatusResponse from(EnergyStatus status) {
        return new EnergyStatusResponse(
                status.battery().socPercent(),
                status.currentRate().name(),
                status.lastDecision().map(ChargeDecisionResponse::from).orElse(null));
    }
}
