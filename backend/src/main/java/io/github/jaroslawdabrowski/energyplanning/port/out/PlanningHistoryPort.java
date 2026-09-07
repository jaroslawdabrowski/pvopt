package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PlanningHistoryPort {

    void record(ChargeDecision decision);

    List<ChargeDecision> findBetween(Instant from, Instant to);

    Optional<ChargeDecision> findLatest();
}
