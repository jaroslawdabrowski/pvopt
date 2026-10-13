package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Why {@link ChargeDecisionPolicy} made a given decision, as a stable code rather than
 * a rendered sentence - the domain stays language-agnostic, and the frontend renders
 * the actual (translatable) message from the code plus {@link ChargeDecision#reasonParams()}.
 */
public enum DecisionReason {
    /** Only in older history: battery had enough at decision time, floor was set to minSoc. */
    ALREADY_AT_TARGET_SOC,
    /** Only in older history: battery had the reserve at decision time - no grid charge, floor at the reserve. */
    RESERVE_HELD,
    BELOW_MIN_SOC,
    FORECAST_SUFFICIENT,
    /** Forecast falls short - grid charge on, target = the reserve; the inverter tops up only if below it during the window. */
    FORECAST_INSUFFICIENT
}
