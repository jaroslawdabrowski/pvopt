package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Install location plus one {@link DirectionPanelSettings} per {@link CompassDirection} -
 * a panel with {@code kwp() == 0} means nothing faces that direction. Replaces the old
 * fixed east/west-only assumption so any combination of up to 4 planes is representable.
 */
public record ForecastSettings(
        double latitude,
        double longitude,
        DirectionPanelSettings north,
        DirectionPanelSettings east,
        DirectionPanelSettings south,
        DirectionPanelSettings west) {

    public ForecastSettings {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, was " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, was " + longitude);
        }
        if (north == null || east == null || south == null || west == null) {
            throw new IllegalArgumentException("all 4 directions must be supplied (kwp=0 for an unused one)");
        }
    }

    public DirectionPanelSettings panelFor(CompassDirection direction) {
        return switch (direction) {
            case NORTH -> north;
            case EAST -> east;
            case SOUTH -> south;
            case WEST -> west;
        };
    }
}
