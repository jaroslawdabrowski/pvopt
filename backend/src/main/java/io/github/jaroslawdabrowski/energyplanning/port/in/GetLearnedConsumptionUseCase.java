package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.ConsumptionEstimator.LearnedConsumption;

import java.util.Optional;

public interface GetLearnedConsumptionUseCase {

    /** Measured consumption of a window's expensive period over recent days, once there's enough of it. */
    Optional<LearnedConsumption> learnedConsumption(ChargeWindow window);
}
