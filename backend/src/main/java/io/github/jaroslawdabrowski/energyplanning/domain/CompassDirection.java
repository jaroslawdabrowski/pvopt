package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * The 4 cardinal directions a PV panel plane can face. Azimuth is fixed per direction
 * (Forecast.Solar convention: 0=south, negative=east, positive=west, range -180..180) -
 * the user only configures declination (tilt) and kWp per direction, not azimuth itself.
 */
public enum CompassDirection {
    NORTH(180),
    EAST(-90),
    SOUTH(0),
    WEST(90);

    private final int azimuthDegrees;

    CompassDirection(int azimuthDegrees) {
        this.azimuthDegrees = azimuthDegrees;
    }

    public int azimuthDegrees() {
        return azimuthDegrees;
    }
}
