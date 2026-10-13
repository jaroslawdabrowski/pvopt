package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalTime;
import java.util.Optional;

/**
 * When each of the two daily decisions runs. Changing either value re-registers the
 * corresponding scheduled job (see {@code SchedulingPort}) - there is no fixed cron
 * baked into code anymore.
 *
 * @param overnightTime shortly before the overnight cheap window starts (e.g. 21:45 for a 22:00 window)
 * @param afternoonTime shortly before the afternoon cheap window starts (e.g. 12:45 for a 13:00 window)
 */
public record ScheduleSettings(LocalTime overnightTime, LocalTime afternoonTime, LocalTime overnightRecheckTime) {

    public ScheduleSettings {
        if (overnightTime == null || afternoonTime == null) {
            throw new IllegalArgumentException("overnightTime and afternoonTime are both required");
        }
    }

    /**
     * The second overnight decision, inside the window (e.g. 03:30): the 21:45 one plans with a
     * forecast that's hours older, and the battery only needs ~2 hours to charge, so planning
     * again shortly before 06:00 uses a fresher forecast at no cost. {@code null} = off.
     */
    public Optional<LocalTime> overnightRecheck() {
        return Optional.ofNullable(overnightRecheckTime);
    }
}
