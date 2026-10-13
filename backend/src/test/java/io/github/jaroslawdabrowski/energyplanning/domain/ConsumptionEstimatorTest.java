package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class ConsumptionEstimatorTest {

    @Test
    void staysOutOfItUntilThereAreEnoughMeasurements() {
        assertThat(ConsumptionEstimator.learn(List.of(outcome(4.0), outcome(5.0)))).isEmpty();
    }

    @Test
    void learnsTheMeanMeasuredLoad() {
        var learned = ConsumptionEstimator.learn(List.of(outcome(4.0), outcome(5.0), outcome(6.25)));

        assertThat(learned).isPresent();
        assertThat(learned.get().kwh()).isEqualTo(5.1);
        assertThat(learned.get().samples()).isEqualTo(3);
    }

    @Test
    void measuresAPeriodAsTheDifferenceOfItsSnapshots() {
        var start = new EnergySnapshot(LocalDateTime.of(2026, 1, 16, 6, 0),
                new DailyEnergyCounters(0.0, 2.1, 1.8, 0.0, 0.0, 0.4), 62);
        var end = new EnergySnapshot(LocalDateTime.of(2026, 1, 16, 13, 0),
                new DailyEnergyCounters(3.4, 7.0, 2.1, 0.2, 0.9, 3.9), 31);

        var outcome = PeriodOutcome.between(ChargeWindow.OVERNIGHT, start, end);

        assertThat(outcome.loadKwh()).isCloseTo(4.9, offset(1e-9));
        assertThat(outcome.pvKwh()).isEqualTo(3.4);
        assertThat(outcome.gridImportKwh()).isCloseTo(0.3, offset(1e-9));
        assertThat(outcome.socAtStartPercent()).isEqualTo(62);
        assertThat(outcome.socAtEndPercent()).isEqualTo(31);
    }

    private static PeriodOutcome outcome(double loadKwh) {
        return new PeriodOutcome(ChargeWindow.OVERNIGHT, LocalDateTime.of(2026, 1, 16, 6, 0),
                LocalDateTime.of(2026, 1, 16, 13, 0), loadKwh, 0, 0, 0, 50, 30);
    }
}
