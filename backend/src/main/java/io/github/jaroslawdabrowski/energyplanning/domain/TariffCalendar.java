package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
     * Which configured window a {@link ChargeWindow} decision is for: the overnight decision
     * owns the window that crosses midnight, the afternoon decision the first one that doesn't.
     */
    public TariffWindow windowFor(ChargeWindow chargeWindow) {
        return cheapWindows.stream()
                .filter(window -> window.crossesMidnight() == (chargeWindow == ChargeWindow.OVERNIGHT))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No cheap tariff window matches the " + chargeWindow
                        + " decision - expected one window crossing midnight and one within the day"));
    }

    /**
     * The expensive period that follows the next end of {@code window} after {@code now}, up to
     * the start of the next cheap window. A decision made shortly before (or during) the window
     * gets the period right after it - e.g. at 21:45, the overnight window's period is
     * 06:00-13:00 tomorrow.
     */
    public ExpensivePeriod expensivePeriodAfter(TariffWindow window, LocalDateTime now) {
        LocalDateTime from = nextOccurrence(window.end(), now);
        LocalDateTime to = cheapWindows.stream()
                .map(w -> nextOccurrence(w.start(), from))
                .min(LocalDateTime::compareTo)
                .orElseThrow();
        return new ExpensivePeriod(from, to);
    }

    /**
     * The expensive period after {@code window} that ends exactly at {@code end}, if any -
     * used when a snapshot is taken at a cheap window's start, to close the period before it.
     */
    public Optional<ExpensivePeriod> expensivePeriodEndingAt(TariffWindow window, LocalDateTime end) {
        var period = expensivePeriodAfter(window, end.minusDays(1));
        if (period.to().isBefore(end)) {
            period = expensivePeriodAfter(window, period.from());
        }
        return period.to().equals(end) ? Optional.of(period) : Optional.empty();
    }

    /** Every clock time at which some cheap window starts or ends - where the expensive periods begin and end. */
    public List<LocalTime> boundaryTimes() {
        return cheapWindows.stream()
                .flatMap(window -> Stream.of(window.start(), window.end()))
                .distinct()
                .sorted()
                .toList();
    }

    /** The first moment strictly after {@code after} at which the clock shows {@code time}. */
    private static LocalDateTime nextOccurrence(LocalTime time, LocalDateTime after) {
        LocalDateTime candidate = after.toLocalDate().atTime(time);
        return candidate.isAfter(after) ? candidate : candidate.plusDays(1);
    }

    /**
     * Returns the cheap tariff window active at the given moment, if any.
     */
    public Optional<TariffWindow> currentWindow(LocalDateTime dateTime) {
        var time = dateTime.toLocalTime();
        return cheapWindows.stream().filter(window -> window.contains(time)).findFirst();
    }
}
