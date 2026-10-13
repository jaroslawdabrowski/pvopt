package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastSolarAdapterTest {

    // watt_hours is cumulative since midnight, reset every day.
    private final Map<String, Long> series = Map.of(
            "2026-01-16 07:30:00", 0L,
            "2026-01-16 09:00:00", 400L,
            "2026-01-16 13:00:00", 2400L,
            "2026-01-16 15:00:00", 3300L,
            "2026-01-16 16:20:00", 3500L,
            "2026-01-17 08:00:00", 100L,
            "2026-01-17 12:00:00", 1800L);

    @Test
    void productionWithinADayIsTheDifferenceOfTheCumulativeValues() {
        assertThat(ForecastSolarAdapter.wattHoursBetween(series,
                LocalDateTime.of(2026, 1, 16, 6, 0), LocalDateTime.of(2026, 1, 16, 13, 0))).isEqualTo(2400L);
        assertThat(ForecastSolarAdapter.wattHoursBetween(series,
                LocalDateTime.of(2026, 1, 16, 15, 0), LocalDateTime.of(2026, 1, 16, 22, 0))).isEqualTo(200L);
    }

    @Test
    void usesTheLatestPointAtOrBeforeEachMoment() {
        assertThat(ForecastSolarAdapter.wattHoursBetween(series,
                LocalDateTime.of(2026, 1, 16, 10, 0), LocalDateTime.of(2026, 1, 16, 14, 0))).isEqualTo(2000L);
    }

    @Test
    void addsUpEachDayWhenThePeriodCrossesMidnight() {
        // 16th from 15:00: 3500 - 3300 = 200; 17th until 12:00: 1800.
        assertThat(ForecastSolarAdapter.wattHoursBetween(series,
                LocalDateTime.of(2026, 1, 16, 15, 0), LocalDateTime.of(2026, 1, 17, 12, 0))).isEqualTo(2000L);
    }

    @Test
    void isZeroWhenTheSeriesHasNoDataForThatDay() {
        assertThat(ForecastSolarAdapter.wattHoursBetween(series,
                LocalDateTime.of(2026, 1, 18, 6, 0), LocalDateTime.of(2026, 1, 18, 13, 0))).isZero();
    }
}
