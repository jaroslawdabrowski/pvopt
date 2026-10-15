package io.github.jaroslawdabrowski.energyplanning.application;

import jakarta.enterprise.context.ApplicationScoped;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetProcessSettingsUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.UpdateProcessSettingsUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.SchedulingPort;

import java.util.Optional;

/**
 * Saving settings always re-registers both scheduled jobs against the new values -
 * simpler and just as correct as diffing what actually changed, and it's what
 * guarantees a job is registered at all the first time settings are ever saved.
 */
@ApplicationScoped
public class ProcessSettingsManager implements GetProcessSettingsUseCase, UpdateProcessSettingsUseCase {

    private final ProcessSettingsPort settingsPort;
    private final SchedulingPort schedulingPort;
    private final ClockPort clockPort;

    public ProcessSettingsManager(ProcessSettingsPort settingsPort, SchedulingPort schedulingPort, ClockPort clockPort) {
        this.settingsPort = settingsPort;
        this.schedulingPort = schedulingPort;
        this.clockPort = clockPort;
    }

    @Override
    public Optional<ProcessSettings> currentSettings() {
        return settingsPort.load();
    }

    @Override
    public ProcessSettings updateSettings(ProcessSettings submitted) {
        // The form doesn't send when estimates changed - that's worked out here, against what's stored.
        var previous = settingsPort.load().map(ProcessSettings::planning).orElse(null);
        var settings = submitted.withPlanning(submitted.planning().withChangesSince(previous, clockPort.nowLocal()));
        settingsPort.save(settings);
        schedulingPort.reschedule(Optional.of(settings));
        return settings;
    }
}
