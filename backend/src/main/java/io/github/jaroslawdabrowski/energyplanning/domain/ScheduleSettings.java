package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalTime;

/**
 * When each of the two daily decisions runs. Changing either value re-registers the
 * corresponding scheduled job (see {@code SchedulingPort}) - there is no fixed cron
 * baked into code anymore.
 *
 * @param overnightTime shortly before the overnight cheap window starts (e.g. 21:45 for a 22:00 window)
 * @param afternoonTime shortly before the afternoon cheap window starts (e.g. 12:45 for a 13:00 window)
 */
public record ScheduleSettings(LocalTime overnightTime, LocalTime afternoonTime) {

    public ScheduleSettings {
        if (overnightTime == null || afternoonTime == null) {
            throw new IllegalArgumentException("overnightTime and afternoonTime are both required");
        }
    }
}
