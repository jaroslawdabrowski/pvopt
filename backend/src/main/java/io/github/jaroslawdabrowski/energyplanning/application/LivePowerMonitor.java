package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettingsNotConfiguredException;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetLivePowerUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;

/**
 * Reads the live power flows for the dashboard. The logger serves one client at a time and the
 * dashboard polls, so a reading is shared for {@link #FRESH_FOR}: several open tabs cost the logger
 * no more than one.
 */
@ApplicationScoped
public class LivePowerMonitor implements GetLivePowerUseCase {

    static final Duration FRESH_FOR = Duration.ofSeconds(5);

    private final ProcessSettingsPort settingsPort;
    private final InverterPort inverterPort;
    private final ClockPort clockPort;
    private LiveReading last;

    public LivePowerMonitor(ProcessSettingsPort settingsPort, InverterPort inverterPort, ClockPort clockPort) {
        this.settingsPort = settingsPort;
        this.inverterPort = inverterPort;
        this.clockPort = clockPort;
    }

    @Override
    public synchronized LiveReading livePower() {
        var now = clockPort.now();
        if (last != null && last.readAt().plus(FRESH_FOR).isAfter(now)) {
            return last;
        }
        var settings = settingsPort.load().orElseThrow(ProcessSettingsNotConfiguredException::new);
        last = new LiveReading(inverterPort.readLivePower(settings.inverterConnection()), now);
        return last;
    }
}
