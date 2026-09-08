package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;

import java.util.Optional;

public interface ProcessSettingsPort {

    Optional<ProcessSettings> load();

    void save(ProcessSettings settings);
}
