package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalTime;

/**
 * A single cheap-tariff window (e.g. 22:00-06:00 or 13:00-15:00 in G12).
 * Allows windows that cross midnight (start > end).
 */
public record TariffWindow(LocalTime start, LocalTime end) {

    public boolean contains(LocalTime time) {
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        // window crosses midnight, e.g. 22:00-06:00
        return !time.isBefore(start) || time.isBefore(end);
    }
}
