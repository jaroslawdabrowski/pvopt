package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * PV panel configuration for one {@link CompassDirection}. A {@code kwp} of 0 means
 * there is no panel facing that direction at all - the forecast adapter skips it
 * entirely rather than calling the API with a zero-capacity plane.
 *
 * @param declinationDegrees tilt from horizontal, in degrees (0-90)
 * @param kwp                installed capacity in this direction, in kWp (0 = no panel)
 * @param inverterInput      which of the inverter's PV inputs (MPPT, 1-based) this plane is wired to -
 *                           only labels the live power flow; null = not set (settings saved before it existed)
 */
public record DirectionPanelSettings(int declinationDegrees, double kwp, Integer inverterInput) {

    public DirectionPanelSettings {
        if (declinationDegrees < 0 || declinationDegrees > 90) {
            throw new IllegalArgumentException("declinationDegrees must be between 0 and 90, was " + declinationDegrees);
        }
        if (kwp < 0) {
            throw new IllegalArgumentException("kwp cannot be negative, was " + kwp);
        }
        if (inverterInput != null && inverterInput < 1) {
            throw new IllegalArgumentException("inverterInput must be 1 or more, was " + inverterInput);
        }
    }

    public DirectionPanelSettings(int declinationDegrees, double kwp) {
        this(declinationDegrees, kwp, null);
    }

    public boolean hasPanel() {
        return kwp > 0;
    }
}
