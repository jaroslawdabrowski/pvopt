package io.github.jaroslawdabrowski.energyplanning.domain;

import java.util.List;
import java.util.Optional;

/**
 * Replaces the hand-entered consumption estimates with what the house actually used: the
 * mean measured load of a window's expensive period over recent days. Until there are
 * enough measurements, the settings value stays in charge - a single odd day (guests,
 * a laundry marathon) shouldn't decide the battery's charge on its own.
 */
public final class ConsumptionEstimator {

    public static final int MIN_SAMPLES = 3;

    private ConsumptionEstimator() {
    }

    /** A learned estimate, or empty when fewer than {@link #MIN_SAMPLES} periods were measured. */
    public static Optional<LearnedConsumption> learn(List<PeriodOutcome> outcomes) {
        if (outcomes.size() < MIN_SAMPLES) {
            return Optional.empty();
        }
        double mean = outcomes.stream().mapToDouble(PeriodOutcome::loadKwh).average().orElseThrow();
        return Optional.of(new LearnedConsumption(Math.round(mean * 10) / 10.0, outcomes.size()));
    }

    public record LearnedConsumption(double kwh, int samples) {
    }
}
