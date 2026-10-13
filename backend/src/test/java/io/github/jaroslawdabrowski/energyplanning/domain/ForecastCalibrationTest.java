package io.github.jaroslawdabrowski.energyplanning.domain;

import io.github.jaroslawdabrowski.energyplanning.domain.ForecastCalibration.Sample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastCalibrationTest {

    @Test
    void waitsForEnoughPeriods() {
        assertThat(ForecastCalibration.learn(List.of(new Sample(5, 4), new Sample(5, 4)))).isEmpty();
    }

    @Test
    void isTheRatioOfMeasuredToForecastPv() {
        var factor = ForecastCalibration.learn(List.of(new Sample(5, 4), new Sample(4, 3), new Sample(1, 0.5)));

        assertThat(factor).hasValueSatisfying(f -> {
            assertThat(f.value()).isEqualTo(0.75);
            assertThat(f.samples()).isEqualTo(3);
        });
    }

    @Test
    void ignoresDaysWithBarelyAnyForecast() {
        assertThat(ForecastCalibration.learn(List.of(new Sample(0.2, 0.5), new Sample(0.3, 0.1), new Sample(0.1, 0.3))))
                .isEmpty();
    }

    @Test
    void neverErasesOrDoublesTheForecast() {
        assertThat(ForecastCalibration.learn(List.of(new Sample(5, 0), new Sample(5, 0), new Sample(5, 0))))
                .hasValueSatisfying(f -> assertThat(f.value()).isEqualTo(0.3));
        assertThat(ForecastCalibration.learn(List.of(new Sample(2, 9), new Sample(2, 9), new Sample(2, 9))))
                .hasValueSatisfying(f -> assertThat(f.value()).isEqualTo(1.5));
    }
}
