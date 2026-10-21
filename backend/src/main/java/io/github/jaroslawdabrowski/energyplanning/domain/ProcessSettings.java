package io.github.jaroslawdabrowski.energyplanning.domain;

import java.util.Optional;

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
        InverterConnectionSettings inverterConnection,
        NotificationSettings notifications,
        PricingSettings pricing) {

    /** Settings without electricity prices - they're optional and only needed for the cost page. */
    public ProcessSettings(PlanningSettings planning, ScheduleSettings schedule, ForecastSettings forecast,
            InverterConnectionSettings inverterConnection, NotificationSettings notifications) {
        this(planning, schedule, forecast, inverterConnection, notifications, null);
    }

    public ProcessSettings {
        if (planning == null || schedule == null || forecast == null || inverterConnection == null) {
            throw new IllegalArgumentException("planning, schedule, forecast and inverterConnection are all required");
        }
        // Optional - settings saved before notifications existed simply have none.
        notifications = notifications == null ? NotificationSettings.NONE : notifications;
    }

    public Optional<PricingSettings> pricingSettings() {
        return Optional.ofNullable(pricing);
    }

    public ProcessSettings withPlanning(PlanningSettings newPlanning) {
        return new ProcessSettings(newPlanning, schedule, forecast, inverterConnection, notifications, pricing);
    }
}
