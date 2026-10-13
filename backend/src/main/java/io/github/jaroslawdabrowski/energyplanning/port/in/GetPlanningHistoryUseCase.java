package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.PlannedDecision;

import java.time.Instant;
import java.util.List;

public interface GetPlanningHistoryUseCase {

    /** Decisions made in the range, each with the measured outcome of its period when there is one. */
    List<PlannedDecision> history(Instant from, Instant to);
}
