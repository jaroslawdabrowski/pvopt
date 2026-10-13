package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import org.junit.jupiter.api.Test;

import io.github.jaroslawdabrowski.energyplanning.domain.DirectionPanelSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // --- caching and the rate limit -------------------------------------------------------

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-16T11:45:00Z"));
    private final FakeClient client = new FakeClient();
    private final ForecastSolarAdapter adapter = new ForecastSolarAdapter(client, clock);
    private final ForecastSettings oneWestPlane = new ForecastSettings(52, 21, none(), none(), none(),
            new DirectionPanelSettings(30, 5.0));
    private final LocalDateTime morningFrom = LocalDateTime.of(2026, 1, 17, 6, 0);
    private final LocalDateTime morningTo = LocalDateTime.of(2026, 1, 17, 13, 0);

    @Test
    void reusesAResponseForAnHour() {
        adapter.productionBetween(morningFrom, morningTo, oneWestPlane);
        clock.advance(Duration.ofMinutes(50));
        var forecast = adapter.productionBetween(morningFrom, morningTo, oneWestPlane);

        assertThat(client.calls).isEqualTo(1);
        assertThat(forecast.expectedKwh()).isEqualTo(1.8);
    }

    @Test
    void asksAgainOnceTheResponseIsAnHourOld() {
        adapter.productionBetween(morningFrom, morningTo, oneWestPlane);
        clock.advance(Duration.ofMinutes(61));
        adapter.productionBetween(morningFrom, morningTo, oneWestPlane);

        assertThat(client.calls).isEqualTo(2);
    }

    @Test
    void fallsBackToTheLastGoodResponseWhenRateLimited() {
        adapter.productionBetween(morningFrom, morningTo, oneWestPlane);
        clock.advance(Duration.ofHours(9));
        client.failWith = new RuntimeException("Too Many Requests, status code 429");

        var forecast = adapter.productionBetween(morningFrom, morningTo, oneWestPlane);

        assertThat(forecast.expectedKwh()).isEqualTo(1.8);
    }

    @Test
    void failsWhenTheLastGoodResponseIsOverADayOld() {
        adapter.productionBetween(morningFrom, morningTo, oneWestPlane);
        clock.advance(Duration.ofHours(25));
        client.failWith = new RuntimeException("Too Many Requests, status code 429");

        assertThatThrownBy(() -> adapter.productionBetween(morningFrom, morningTo, oneWestPlane))
                .hasMessageContaining("429");
    }

    @Test
    void neverReadsAMissingDayAsZero() {
        // The series only reaches the 17th - asking for the 18th must fail, not return 0 kWh.
        assertThatThrownBy(() -> adapter.productionBetween(LocalDateTime.of(2026, 1, 18, 6, 0),
                LocalDateTime.of(2026, 1, 18, 13, 0), oneWestPlane)).hasMessageContaining("no data");
    }

    private static DirectionPanelSettings none() {
        return new DirectionPanelSettings(0, 0);
    }

    private final class FakeClient implements ForecastSolarRestClient {
        int calls;
        RuntimeException failWith;

        @Override
        public ForecastSolarEstimateResponse estimate(double latitude, double longitude, int declination, int azimuth,
                double kwp) {
            calls++;
            if (failWith != null) {
                throw failWith;
            }
            return new ForecastSolarEstimateResponse(new ForecastSolarEstimateResponse.Result(Map.of(), series));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
