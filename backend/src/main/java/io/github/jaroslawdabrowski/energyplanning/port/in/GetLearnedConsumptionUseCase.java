package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.ConsumptionLearning;

public interface GetLearnedConsumptionUseCase {

    /** Measured consumption of a window's expensive period over recent days, and how far learning has got. */
    ConsumptionLearning learnedConsumption(ChargeWindow window);
}
