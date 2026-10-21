package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class EnergyCostCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 15);
    // G12 rates from the September 2026 invoice, gross.
    private final PricingSettings pricing = new PricingSettings(0.611, 1.249, 44.38, 1.23);
    private final TariffCalendar g12 = new TariffCalendar(List.of(
            new TariffWindow(LocalTime.of(22, 0), LocalTime.of(6, 0)),
            new TariffWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))));

    @Test
    void pricesBoughtEnergyByTheZoneOfTheHourItWasBoughtIn() {
        // 2 kWh bought overnight (cheap), 1 kWh between 06 and 07 (expensive).
        var cost = EnergyCostCalculator.dailyCost(DAY, List.of(
                snapshot(6, 0, 0, 2.0, 0, 2.0),
                snapshot(7, 0, 0, 3.0, 0, 3.0)), g12, pricing, h -> 0.3);

        assertThat(cost.importCheapKwh()).isCloseTo(2.0, offset(1e-9));
        assertThat(cost.importExpensiveKwh()).isCloseTo(1.0, offset(1e-9));
        assertThat(cost.importCost()).isCloseTo(2 * 0.611 + 1.249, offset(1e-9));
        assertThat(cost.baselineCost()).isCloseTo(2 * 0.611 + 1.249, offset(1e-9));
    }

    @Test
    void creditsExportAtThatHoursMarketPriceTimesTheDepositMultiplier() {
        Map<LocalDateTime, Double> prices = new HashMap<>();
        prices.put(DAY.atTime(11, 0), 0.33);
        prices.put(DAY.atTime(12, 0), 0.27);
        var cost = EnergyCostCalculator.dailyCost(DAY, List.of(
                snapshot(11, 0, 0, 0, 0, 0),
                snapshot(12, 0, 0, 0, 1.5, 0.4),
                snapshot(13, 0, 0, 0, 3.5, 0.8)), g12, pricing, EnergyCostCalculator.lookup(prices));

        assertThat(cost.exportKwh()).isCloseTo(3.5, offset(1e-9));
        assertThat(cost.exportValue()).isCloseTo((1.5 * 0.33 + 2.0 * 0.27) * 1.23, offset(1e-9));
        assertThat(cost.netCost()).isCloseTo(-cost.exportValue(), offset(1e-9));
        // Without PV and battery the 0.8 kWh used would have been bought at the expensive rate.
        assertThat(cost.savings()).isCloseTo(0.8 * 1.249 + cost.exportValue(), offset(1e-9));
    }

    @Test
    void valuesExportAtANegativeMarketPriceAsZero() {
        var cost = EnergyCostCalculator.dailyCost(DAY, List.of(
                snapshot(12, 0, 0, 0, 0, 0),
                snapshot(13, 0, 0, 0, 2.0, 0)), g12, pricing, h -> -0.05);

        assertThat(cost.exportValue()).isZero();
    }

    @Test
    void flagsExportWithoutAPriceYet() {
        var cost = EnergyCostCalculator.dailyCost(DAY, List.of(
                snapshot(12, 0, 0, 0, 0, 0),
                snapshot(13, 0, 0, 0, 1.0, 0)), g12, pricing, h -> null);

        assertThat(cost.exportPriceGaps()).isTrue();
    }

    @Test
    void spreadsAGapOverItsHoursAndMarksTheDayApproximate() {
        // Nothing between 05:00 and 08:00: 3 kWh bought, 1 h cheap (05-06), 2 h expensive.
        var cost = EnergyCostCalculator.dailyCost(DAY, List.of(
                snapshot(5, 0, 0, 1.0, 0, 1.0),
                snapshot(8, 0, 0, 4.0, 0, 4.0)), g12, pricing, h -> 0.3);

        assertThat(cost.importCheapKwh()).isCloseTo(2.0, offset(1e-9));
        assertThat(cost.importExpensiveKwh()).isCloseTo(2.0, offset(1e-9));
        assertThat(cost.approximate()).isTrue();
        assertThat(cost.complete()).isFalse();
    }

    @Test
    void aSnapshotJustBeforeMidnightClosesTheDay() {
        var hourly = new java.util.ArrayList<EnergySnapshot>();
        for (int h = 1; h <= 23; h++) {
            hourly.add(snapshot(h, 0, 0, h * 0.5, 0, h * 0.5));
        }
        hourly.add(snapshot(23, 58, 0, 12.0, 0, 12.0));

        var cost = EnergyCostCalculator.dailyCost(DAY, hourly, g12, pricing, h -> 0.3);

        assertThat(cost.coveredHours()).isEqualTo(24);
        assertThat(cost.complete()).isTrue();
        assertThat(cost.loadKwh()).isCloseTo(12.0, offset(1e-9));
    }

    private static EnergySnapshot snapshot(int hour, int minute, double pv, double imported, double exported,
            double load) {
        return new EnergySnapshot(DAY.atTime(hour, minute),
                new DailyEnergyCounters(pv, load, imported, exported, 0, 0), 50);
    }
}
