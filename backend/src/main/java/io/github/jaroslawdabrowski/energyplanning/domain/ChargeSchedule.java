package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Grid-charge schedule to be written to the inverter for one of the two daily windows.
 * A purely domain-level description - no protocol/register/TOU-slot details; the
 * adapter maps {@link #window()} to whatever concrete inverter slot(s) that means.
 */
public record ChargeSchedule(ChargeWindow window, boolean gridChargeEnabled, int targetSocPercent) {

    public ChargeSchedule {
        if (targetSocPercent < 0 || targetSocPercent > 100) {
            throw new IllegalArgumentException("targetSocPercent must be between 0 and 100, was " + targetSocPercent);
        }
    }
}
