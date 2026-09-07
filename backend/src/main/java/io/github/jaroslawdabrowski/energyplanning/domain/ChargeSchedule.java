package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalTime;

/**
 * Grid-charge schedule to be written to the inverter.
 * A purely domain-level description - no protocol/register details.
 */
public record ChargeSchedule(boolean gridChargeEnabled, LocalTime start, LocalTime end, int targetSocPercent) {

    public ChargeSchedule {
        if (targetSocPercent < 0 || targetSocPercent > 100) {
            throw new IllegalArgumentException("targetSocPercent must be between 0 and 100, was " + targetSocPercent);
        }
    }

    public static ChargeSchedule disabled(LocalTime start, LocalTime end, int currentSocPercent) {
        return new ChargeSchedule(false, start, end, currentSocPercent);
    }
}
