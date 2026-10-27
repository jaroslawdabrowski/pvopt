package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class HourlyForecastCalibrationTest {

    private final List<EnergySnapshot> snapshots = new ArrayList<>();
    private final Map<LocalDateTime, Double> forecast = new HashMap<>();

    /** One day's 07:00-11:00: forecast 1 kWh every hour, measured as given per hour from 07:00. */
    private void day(LocalDate date, double... measured) {
        double pv = 0;
        for (int i = 0; i <= measured.length; i++) {
            snapshots.add(new EnergySnapshot(date.atTime(7 + i, 0), new DailyEnergyCounters(pv, 0, 0, 0, 0, 0), 50));
            if (i < measured.length) {
                forecast.put(date.atTime(7 + i, 0), 1.0);
                pv += measured[i];
            }
        }
    }

    @Test
    void eachHourGetsTheRatioOfMeasuredToForecastOverTheDays() {
        day(LocalDate.of(2026, 10, 21), 0.1, 0.5, 1.0, 1.2);
        day(LocalDate.of(2026, 10, 22), 0.3, 0.6, 0.8, 1.2);
        day(LocalDate.of(2026, 10, 23), 0.2, 0.4, 0.9, 1.2);

        var calibration = HourlyForecastCalibration.learn(snapshots, forecast);

        assertThat(calibration.factor(7)).isCloseTo(0.2, offset(1e-9));
        assertThat(calibration.factor(8)).isCloseTo(0.5, offset(1e-9));
        assertThat(calibration.factor(10)).isCloseTo(1.2, offset(1e-9));
        assertThat(calibration.factor(14)).isEqualTo(1.0);
    }

    @Test
    void needsThreeDaysForAnHour() {
        day(LocalDate.of(2026, 10, 21), 0.1);
        day(LocalDate.of(2026, 10, 22), 0.1);

        assertThat(HourlyForecastCalibration.learn(snapshots, forecast).factor(7)).isEqualTo(1.0);
    }

    @Test
    void ignoresAnHourWithTooLittleForecastToSayAnything() {
        for (int d = 0; d < 3; d++) {
            day(LocalDate.of(2026, 10, 21).plusDays(d), 0.05);
            forecast.put(LocalDate.of(2026, 10, 21).plusDays(d).atTime(7, 0), 0.05);
        }

        assertThat(HourlyForecastCalibration.learn(snapshots, forecast).factor(7)).isEqualTo(1.0);
    }

    @Test
    void isClampedSoOneBrokenHourCantEraseOrTripleTheForecast() {
        for (int d = 0; d < 3; d++) {
            day(LocalDate.of(2026, 10, 21).plusDays(d), 0.0, 5.0);
        }

        var calibration = HourlyForecastCalibration.learn(snapshots, forecast);

        assertThat(calibration.factor(7)).isEqualTo(HourlyForecastCalibration.MIN_FACTOR);
        assertThat(calibration.factor(8)).isEqualTo(HourlyForecastCalibration.MAX_FACTOR);
    }

    @Test
    void scalesEachSlotByTheHourItStartsIn() {
        for (int d = 0; d < 3; d++) {
            day(LocalDate.of(2026, 10, 21).plusDays(d), 0.5, 1.0);
        }
        var calibration = HourlyForecastCalibration.learn(snapshots, forecast);
        var profile = new ProductionProfile(LocalDateTime.of(2026, 10, 24, 6, 0), List.of(0.2, 1.0, 2.0),
                List.of(1.0, 1.0, 1.0));

        var calibrated = calibration.apply(profile);

        assertThat(calibrated.kwhPerSlot()).containsExactly(0.2, 0.5, 2.0);
        assertThat(calibration.days(profile)).isEqualTo(3);
    }
}
