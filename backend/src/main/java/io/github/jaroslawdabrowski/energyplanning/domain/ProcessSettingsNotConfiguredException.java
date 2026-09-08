package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Thrown when a use case needs {@link ProcessSettings} but nothing has been saved yet.
 * The scheduled jobs catch this and just log it (no action taken, per design - see
 * {@code SchedulingPort}); the web layer maps it to a 409 response.
 */
public class ProcessSettingsNotConfiguredException extends RuntimeException {

    public ProcessSettingsNotConfiguredException() {
        super("No process settings configured yet - save them from the settings panel first");
    }
}
