package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Aktualny stan magazynu energii, odczytany z falownika.
 */
public record BatteryStatus(int socPercent) {

    public BatteryStatus {
        if (socPercent < 0 || socPercent > 100) {
            throw new IllegalArgumentException("socPercent must be between 0 and 100, was " + socPercent);
        }
    }

    public boolean isAtOrAbove(int thresholdPercent) {
        return socPercent >= thresholdPercent;
    }

    public boolean isBelow(int thresholdPercent) {
        return socPercent < thresholdPercent;
    }
}
