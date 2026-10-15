package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Where learning a window's consumption stands: the learned figure once there are enough
 * measured periods, how many there are, and - when the estimate was changed recently - the
 * moment measurements count from.
 */
public record ConsumptionLearning(Optional<ConsumptionEstimator.LearnedConsumption> learned, int samples,
        Optional<LocalDateTime> countingFrom) {
}
