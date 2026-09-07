package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;

import java.time.Instant;
import java.util.Map;

public record ChargeDecisionResponse(
        Instant decidedAt,
        boolean chargeFromGrid,
        int targetSocPercent,
        String reasonCode,
        Map<String, Double> reasonParams) {

    static ChargeDecisionResponse from(ChargeDecision decision) {
        return new ChargeDecisionResponse(decision.decidedAt(), decision.chargeFromGrid(), decision.targetSocPercent(),
                decision.reason().name(), decision.reasonParams());
    }
}
