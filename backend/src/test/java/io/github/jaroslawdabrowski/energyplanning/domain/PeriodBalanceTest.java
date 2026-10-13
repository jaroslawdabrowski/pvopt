package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class PeriodBalanceTest {

    private static final List<Double> HOURS = Collections.nCopies(7, 1.0);

    @Test
    void theReserveIsTheLargestRunningDeficit() {
        // The real 2026-10-14 morning forecast (east 3 kWp + west 5 kWp), 5.6 kWh spread flat.
        var balance = PeriodBalance.of(Collections.nCopies(7, 0.806),
                List.of(0.0, 0.3, 0.59, 0.83, 0.99, 1.08, 1.1), HOURS);

        assertThat(balance.reserveKwh()).isCloseTo(1.528, offset(1e-3));
        assertThat(balance.peakHourOffset()).isEqualTo(3.0); // by 09:00
        assertThat(balance.consumptionKwh() - balance.productionKwh()).isCloseTo(0.752, offset(1e-3));
    }

    @Test
    void needsNothingWhenThePanelsLeadThroughout() {
        var balance = PeriodBalance.of(Collections.nCopies(7, 0.5), Collections.nCopies(7, 0.8), HOURS);

        assertThat(balance.reserveKwh()).isZero();
    }

    @Test
    void anEveningWithoutSunNeedsTheWholeConsumption() {
        var balance = PeriodBalance.of(Collections.nCopies(7, 0.9),
                List.of(0.9, 0.6, 0.1, 0.0, 0.0, 0.0, 0.0), HOURS);

        assertThat(balance.reserveKwh()).isCloseTo(4.7, offset(1e-9));
        assertThat(balance.peakHourOffset()).isEqualTo(7.0);
    }
}
