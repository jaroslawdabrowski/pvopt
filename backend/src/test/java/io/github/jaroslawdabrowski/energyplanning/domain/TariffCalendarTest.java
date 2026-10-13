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

    @Test
    void assignsTheMidnightCrossingWindowToTheOvernightDecision() {
        assertThat(calendar.windowFor(ChargeWindow.OVERNIGHT))
                .isEqualTo(new TariffWindow(LocalTime.of(22, 0), LocalTime.of(6, 0)));
        assertThat(calendar.windowFor(ChargeWindow.AFTERNOON))
                .isEqualTo(new TariffWindow(LocalTime.of(13, 0), LocalTime.of(15, 0)));
    }

    @Test
    void overnightDecisionBeforeTheWindowCoversTomorrowMorning() {
        var period = calendar.expensivePeriodAfter(calendar.windowFor(ChargeWindow.OVERNIGHT),
                LocalDateTime.of(2026, 1, 15, 21, 45));

        assertThat(period).isEqualTo(new ExpensivePeriod(LocalDateTime.of(2026, 1, 16, 6, 0),
                LocalDateTime.of(2026, 1, 16, 13, 0)));
        assertThat(period.hours()).isEqualTo(7.0);
    }

    @Test
    void overnightDecisionInsideTheWindowAfterMidnightCoversThisMorning() {
        var period = calendar.expensivePeriodAfter(calendar.windowFor(ChargeWindow.OVERNIGHT),
                LocalDateTime.of(2026, 1, 16, 0, 30));

        assertThat(period.from()).isEqualTo(LocalDateTime.of(2026, 1, 16, 6, 0));
        assertThat(period.to()).isEqualTo(LocalDateTime.of(2026, 1, 16, 13, 0));
    }

    @Test
    void afternoonDecisionCoversThisEvening() {
        var period = calendar.expensivePeriodAfter(calendar.windowFor(ChargeWindow.AFTERNOON),
                LocalDateTime.of(2026, 1, 15, 12, 45));

        assertThat(period).isEqualTo(new ExpensivePeriod(LocalDateTime.of(2026, 1, 15, 15, 0),
                LocalDateTime.of(2026, 1, 15, 22, 0)));
    }

    @Test
    void afternoonDecisionRunByHandAfterTheWindowCoversTomorrowEvening() {
        var period = calendar.expensivePeriodAfter(calendar.windowFor(ChargeWindow.AFTERNOON),
                LocalDateTime.of(2026, 1, 15, 16, 0));

        assertThat(period.from()).isEqualTo(LocalDateTime.of(2026, 1, 16, 15, 0));
    }
}
