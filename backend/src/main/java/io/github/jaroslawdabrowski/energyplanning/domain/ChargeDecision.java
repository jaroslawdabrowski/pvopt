package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of the planning engine's decision: whether and how to charge the battery
 * from the grid, plus the reason as a code and numeric parameters - not a rendered
 * sentence, so the UI can present it in whatever language the user has chosen.
 */
public record ChargeDecision(
        Instant decidedAt,
        ChargeWindow window,
        boolean chargeFromGrid,
        int targetSocPercent,
        DecisionReason reason,
        Map<String, Double> reasonParams) {

    /** The same decision with extra reason parameters (e.g. context only the planner knows). */
    public ChargeDecision withReasonParams(Map<String, Double> extra) {
        var merged = new HashMap<>(reasonParams);
        merged.putAll(extra);
        return new ChargeDecision(decidedAt, window, chargeFromGrid, targetSocPercent, reason, Map.copyOf(merged));
    }
}
