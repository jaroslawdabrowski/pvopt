package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.openmeteo;

import io.github.jaroslawdabrowski.energyplanning.domain.DirectionPanelSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ExpensivePeriod;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class OpenMeteoAdapterTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final DirectionPanelSettings NONE = new DirectionPanelSettings(0, 0);
    /** 3 kWp east, 5 kWp west - like the real install. */
    private static final ForecastSettings SETTINGS = new ForecastSettings(50.04, 22.0, NONE,
            new DirectionPanelSettings(30, 3), NONE, new DirectionPanelSettings(30, 5));
    private static final ExpensivePeriod MORNING = new ExpensivePeriod(
            LocalDateTime.of(2026, 10, 22, 6, 0), LocalDateTime.of(2026, 10, 22, 13, 0));

    private final MovableClock clock = new MovableClock();
    private final FakeOpenMeteo api = new FakeOpenMeteo();
    private final FakeArchive archive = new FakeArchive();
    private final OpenMeteoAdapter adapter = new OpenMeteoAdapter(api, archive, clock);

    @Test
    void eachHourIsTheIrradianceOfTheHourEndingAfterItTimesKwpAndThePerformanceRatio() {
        var profile = adapter.productionProfile(MORNING, SETTINGS);

        // 06-07 is the value stamped 07:00: east 100 W/m² x 3 kWp + west 40 W/m² x 5 kWp, x 0.85.
        assertThat(profile.kwhPerSlot()).hasSize(7);
        assertThat(profile.kwhPerSlot().getFirst()).isCloseTo((0.1 * 3 + 0.04 * 5) * 0.85, offset(1e-9));
        assertThat(api.calls).containsExactlyInAnyOrder(-90, 90);
    }

    @Test
    void countsOnlyThePartOfAnHourInsideThePeriod() {
        var irradiance = Map.of(LocalDateTime.of(2026, 10, 22, 7, 0), 1000.0);

        double kwh = OpenMeteoAdapter.kwhPerKwpBetween(irradiance,
                LocalDateTime.of(2026, 10, 22, 6, 30), LocalDateTime.of(2026, 10, 22, 7, 0));

        assertThat(kwh).isCloseTo(0.5 * OpenMeteoAdapter.PERFORMANCE_RATIO, offset(1e-9));
    }

    @Test
    void aMissingHourFailsInsteadOfReadingAsNoSun() {
        api.lastHour = 9; // UTC: 11:00 in Warsaw, the period runs to 13:00

        assertThatThrownBy(() -> adapter.productionProfile(MORNING, SETTINGS)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reusesAResponseForAnHourThenAsksAgain() {
        adapter.productionProfile(MORNING, SETTINGS);
        clock.advance(Duration.ofMinutes(59));
        adapter.productionProfile(MORNING, SETTINGS);
        assertThat(api.calls).hasSize(2);

        clock.advance(Duration.ofMinutes(2));
        adapter.productionProfile(MORNING, SETTINGS);
        assertThat(api.calls).hasSize(4);
    }

    @Test
    void fallsBackToTheLastGoodResponseWhenACallFails() {
        var fresh = adapter.productionProfile(MORNING, SETTINGS);
        clock.advance(Duration.ofHours(5));
        api.failing = true;

        assertThat(adapter.productionProfile(MORNING, SETTINGS).kwhPerSlot()).isEqualTo(fresh.kwhPerSlot());

        clock.advance(Duration.ofHours(20));
        assertThatThrownBy(() -> adapter.productionProfile(MORNING, SETTINGS)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void asksInUtcAndConvertsToTheClocksZone() {
        api.peakUtcHour = 10; // 12:00 in Warsaw (CEST): the slot 11-12
        var profile = adapter.productionProfile(MORNING, SETTINGS);

        assertThat(api.timezones).containsOnly("GMT");
        assertThat(profile.kwhPerSlot().get(5)).isGreaterThan(profile.kwhPerSlot().get(4));
    }

    @Test
    void pastProductionIsKeyedByEachHoursLocalStartAcrossADstChange() {
        // 25 October: clocks go back at 03:00 CEST. 09:00 UTC is 11:00 before and 10:00 after.
        var past = adapter.pastHourlyProduction(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25), SETTINGS);

        assertThat(archive.timezones).containsOnly("GMT");
        assertThat(past.get(LocalDateTime.of(2026, 10, 24, 10, 0))).isCloseTo((0.5 * 3 + 0.5 * 5) * 0.85, offset(1e-9));
        assertThat(past.get(LocalDateTime.of(2026, 10, 25, 9, 0))).isCloseTo((0.5 * 3 + 0.5 * 5) * 0.85, offset(1e-9));
        assertThat(past.get(LocalDateTime.of(2026, 10, 24, 9, 0))).isZero();
    }

    /** East gets 100 W/m² every hour of the day, west 40; hours run up to {@link #lastHour}. */
    private static final class FakeOpenMeteo implements OpenMeteoRestClient {
        final List<Integer> calls = new ArrayList<>();
        final List<String> timezones = new ArrayList<>();
        int lastHour = 23;
        int peakUtcHour = -1;
        boolean failing;

        @Override
        public OpenMeteoResponse tiltedIrradiance(double latitude, double longitude, int tilt, int azimuth,
                String hourly, int forecastDays, String timezone) {
            calls.add(azimuth);
            timezones.add(timezone);
            if (failing) {
                throw new RuntimeException("503");
            }
            var times = new ArrayList<String>();
            var values = new ArrayList<Double>();
            for (int hour = 0; hour <= lastHour; hour++) {
                times.add(LocalDateTime.of(2026, 10, 22, hour, 0).toString());
                values.add(hour == peakUtcHour ? 500.0 : azimuth < 0 ? 100.0 : 40.0);
            }
            return new OpenMeteoResponse(new OpenMeteoResponse.Hourly(times, values));
        }
    }

    /** 500 W/m² on both planes in the hour ending 09:00 UTC each day, nothing otherwise. */
    private static final class FakeArchive implements OpenMeteoHistoricalRestClient {
        final List<String> timezones = new ArrayList<>();

        @Override
        public OpenMeteoResponse tiltedIrradiance(double latitude, double longitude, int tilt, int azimuth,
                String hourly, String startDate, String endDate, String timezone) {
            timezones.add(timezone);
            var times = new ArrayList<String>();
            var values = new ArrayList<Double>();
            for (var day = LocalDate.parse(startDate); !day.isAfter(LocalDate.parse(endDate)); day = day.plusDays(1)) {
                for (int hour = 0; hour < 24; hour++) {
                    times.add(day.atTime(hour, 0).toString());
                    values.add(hour == 9 ? 500.0 : 0.0);
                }
            }
            return new OpenMeteoResponse(new OpenMeteoResponse.Hourly(times, values));
        }
    }

    private static final class MovableClock extends Clock {
        private Instant now = LocalDateTime.of(2026, 10, 21, 21, 45).atZone(WARSAW).toInstant();

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return WARSAW;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
