package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Why {@link ChargeDecisionPolicy} made a given decision, as a stable code rather than
 * a rendered sentence - the domain stays language-agnostic, and the frontend renders
 * the actual (translatable) message from the code plus {@link ChargeDecision#reasonParams()}.
 */
public enum DecisionReason {
    ALREADY_AT_TARGET_SOC,
    BELOW_MIN_SOC,
    FORECAST_SUFFICIENT,
    FORECAST_INSUFFICIENT
}
