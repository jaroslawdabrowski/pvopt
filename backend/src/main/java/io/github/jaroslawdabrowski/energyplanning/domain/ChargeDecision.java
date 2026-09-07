package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Result of the planning engine's decision: whether and how to charge the battery
 * from the grid, plus the reason as a code and numeric parameters - not a rendered
 * sentence, so the UI can present it in whatever language the user has chosen.
 */
public record ChargeDecision(
        Instant decidedAt,
        boolean chargeFromGrid,
        int targetSocPercent,
        DecisionReason reason,
        Map<String, Double> reasonParams) {
}
