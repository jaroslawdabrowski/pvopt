package io.github.jaroslawdabrowski.energyplanning.adapter.out.clock;

import jakarta.enterprise.context.ApplicationScoped;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@ApplicationScoped
public class SystemClockAdapter implements ClockPort {

    private final ZoneId zoneId = ZoneId.systemDefault();

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public LocalDateTime nowLocal() {
        return LocalDateTime.now(zoneId);
    }
}
