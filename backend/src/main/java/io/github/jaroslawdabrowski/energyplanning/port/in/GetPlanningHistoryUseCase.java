package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;

import java.time.Instant;
import java.util.List;

public interface GetPlanningHistoryUseCase {

    List<ChargeDecision> history(Instant from, Instant to);
}
