package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * The complete set of user-editable process configuration - planning policy, the daily
 * schedule, the PV forecast install, and the inverter connection - persisted as a single
 * JSON blob (see {@code ProcessSettingsPort}). Until the user saves this once from the
 * settings panel, nothing is configured: both scheduled jobs stay unregistered and log
 * that fact instead of running (see {@code ProcessSettingsNotConfiguredException}).
 */
public record ProcessSettings(
        PlanningSettings planning,
        ScheduleSettings schedule,
        ForecastSettings forecast,
        InverterConnectionSettings inverterConnection) {

    public ProcessSettings {
        if (planning == null || schedule == null || forecast == null || inverterConnection == null) {
            throw new IllegalArgumentException("planning, schedule, forecast and inverterConnection are all required");
        }
    }
}
