package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;

public interface UpdateProcessSettingsUseCase {

    /** Saves the settings and re-registers the scheduled jobs to match. */
    ProcessSettings updateSettings(ProcessSettings settings);
}
