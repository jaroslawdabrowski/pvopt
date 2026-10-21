package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.LivePower;

import java.time.Instant;

/** Inbound port: the power flows right now, for the dashboard's live panel. */
public interface GetLivePowerUseCase {

    LiveReading livePower();

    record LiveReading(LivePower power, Instant readAt) {
    }
}
