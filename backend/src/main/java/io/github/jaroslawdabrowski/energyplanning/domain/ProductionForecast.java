package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalDate;

/**
 * Prognoza spodziewanej produkcji energii z paneli PV dla danego dnia.
 */
public record ProductionForecast(LocalDate date, double expectedKwh) {

    public ProductionForecast {
        if (expectedKwh < 0) {
            throw new IllegalArgumentException("expectedKwh cannot be negative, was " + expectedKwh);
        }
    }

    public boolean coversAtLeast(double requiredKwh) {
        return expectedKwh >= requiredKwh;
    }
}
