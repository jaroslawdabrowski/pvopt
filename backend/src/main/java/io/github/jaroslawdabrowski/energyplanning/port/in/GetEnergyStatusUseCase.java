package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.TariffRate;

import java.util.Optional;

public interface GetEnergyStatusUseCase {

    EnergyStatus currentStatus();

    record EnergyStatus(BatteryStatus battery, TariffRate currentRate, Optional<ChargeDecision> lastDecision) {
    }
}
