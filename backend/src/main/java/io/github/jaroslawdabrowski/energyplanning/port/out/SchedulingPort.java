package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;

import java.util.Optional;

/**
 * Lets the application layer tell the scheduling infrastructure to re-register its jobs
 * after settings change - whatever changed (schedule times or not), both daily jobs are
 * unregistered and, if settings are present, re-registered against the current times.
 * An empty {@code settings} unregisters both jobs entirely.
 */
public interface SchedulingPort {

    void reschedule(Optional<ProcessSettings> settings);
}
