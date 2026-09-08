package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;

import java.util.Optional;

public interface GetProcessSettingsUseCase {

    Optional<ProcessSettings> currentSettings();
}
