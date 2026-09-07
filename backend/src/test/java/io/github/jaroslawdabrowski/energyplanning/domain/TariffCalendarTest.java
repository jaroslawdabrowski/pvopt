package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TariffCalendarTest {

    // G12: 22:00-06:00 and 13:00-15:00
    private final TariffCalendar calendar = new TariffCalendar(List.of(
            new TariffWindow(LocalTime.of(22, 0), LocalTime.of(6, 0)),
            new TariffWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))));

    @Test
    void isCheapJustAfterMidnightWithinOvernightWindow() {
        assertThat(calendar.rateAt(LocalDateTime.of(2026, 1, 15, 2, 30))).isEqualTo(TariffRate.CHEAP);
    }

    @Test
    void isCheapLateEveningWithinOvernightWindow() {
        assertThat(calendar.rateAt(LocalDateTime.of(2026, 1, 15, 23, 0))).isEqualTo(TariffRate.CHEAP);
    }

    @Test
    void isCheapDuringAfternoonWindow() {
        assertThat(calendar.rateAt(LocalDateTime.of(2026, 1, 15, 14, 0))).isEqualTo(TariffRate.CHEAP);
    }

    @Test
    void isExpensiveOutsideCheapWindows() {
        assertThat(calendar.rateAt(LocalDateTime.of(2026, 1, 15, 10, 0))).isEqualTo(TariffRate.EXPENSIVE);
        assertThat(calendar.rateAt(LocalDateTime.of(2026, 1, 15, 18, 0))).isEqualTo(TariffRate.EXPENSIVE);
    }

    @Test
    void boundaryIsInclusiveAtStartAndExclusiveAtEnd() {
        assertThat(calendar.rateAt(LocalDateTime.of(2026, 1, 15, 6, 0))).isEqualTo(TariffRate.EXPENSIVE);
        assertThat(calendar.rateAt(LocalDateTime.of(2026, 1, 15, 22, 0))).isEqualTo(TariffRate.CHEAP);
    }
}
