package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The stretch of expensive tariff right after a cheap window, until the next cheap window
 * starts (in G12: 06:00-13:00 after the night, 15:00-22:00 after the afternoon). This is
 * what a grid charge in that cheap window has to carry the house through - anything the
 * battery needs later is the next window's decision.
 */
public record ExpensivePeriod(LocalDateTime from, LocalDateTime to) {

    public ExpensivePeriod {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("an expensive period must end after it starts: " + from + " - " + to);
        }
    }

    public double hours() {
        return Duration.between(from, to).toMinutes() / 60.0;
    }
}
