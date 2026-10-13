package io.github.jaroslawdabrowski.energyplanning.domain;

import java.util.Optional;

/** A decision together with what was measured in the expensive period it was made for, once that period is over. */
public record PlannedDecision(ChargeDecision decision, Optional<PeriodOutcome> outcome) {
}
