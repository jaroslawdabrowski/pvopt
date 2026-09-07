package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Pure G12 tariff logic: determines whether a given moment falls in a cheap window.
 * The window list is supplied from outside (configuration) so the tariff can be
 * adjusted without changing domain code.
 */
public class TariffCalendar {

    private final List<TariffWindow> cheapWindows;

    public TariffCalendar(List<TariffWindow> cheapWindows) {
        if (cheapWindows == null || cheapWindows.isEmpty()) {
            throw new IllegalArgumentException("cheapWindows must not be empty");
        }
        this.cheapWindows = List.copyOf(cheapWindows);
    }

    public TariffRate rateAt(LocalDateTime dateTime) {
        return currentWindow(dateTime).isPresent() ? TariffRate.CHEAP : TariffRate.EXPENSIVE;
    }

    /**
     * Returns the cheap tariff window active at the given moment, if any.
     */
    public Optional<TariffWindow> currentWindow(LocalDateTime dateTime) {
        var time = dateTime.toLocalTime();
        return cheapWindows.stream().filter(window -> window.contains(time)).findFirst();
    }
}
