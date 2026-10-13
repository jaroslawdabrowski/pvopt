package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeDecisionPolicyTest {

    private final ChargeDecisionPolicy policy = new ChargeDecisionPolicy();
    private final Instant now = Instant.parse("2026-01-15T22:30:00Z");
    /** A 10 kWh battery - every 1 kWh of shortfall is 10 percentage points. */
    private final PlanningPolicyConfig config = new PlanningPolicyConfig(20, 100, 10.0);
    private final PlanningPolicyConfig unknownCapacity = new PlanningPolicyConfig(20, 100, 0.0);
    private static final double REQUIRED_KWH = 18.0;

    @Test
    void chargesToMinimumWhenBelowSafetyFloorAndForecastCoversTheRest() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(10), forecast(50.0),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(20);
        assertThat(decision.reason()).isEqualTo(DecisionReason.BELOW_MIN_SOC);
    }

    @Test
    void chargesForTheShortfallWhenBelowSafetyFloorAndForecastFallsShort() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(10), forecast(15.0),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(50);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
    }

    @Test
    void skipsGridChargeWhenForecastIsSufficient() {
        var decision = policy.decide(now, ChargeWindow.AFTERNOON, new BatteryStatus(50), forecast(20.0),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.window()).isEqualTo(ChargeWindow.AFTERNOON);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_SUFFICIENT);
        assertThat(decision.targetSocPercent()).isEqualTo(20);
    }

    @Test
    void chargesOnlyForTheEnergyTheForecastWontCover() {
        // 18 kWh needed, 14.5 kWh forecast -> 3.5 kWh short -> 35 points above the 20% minimum.
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(30), forecast(14.5),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(55);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
        assertThat(decision.reasonParams()).containsEntry("targetSoc", 55.0);
    }

    @Test
    void roundsTheShortfallUpToAWholePercent() {
        // 0.31 kWh short on a 10 kWh battery is 3.1 points - charge to 24%, not 23%.
        var decision = policy.decide(now, ChargeWindow.AFTERNOON, new BatteryStatus(20), forecast(17.69),
                REQUIRED_KWH, config);

        assertThat(decision.targetSocPercent()).isEqualTo(24);
    }

    @Test
    void capsTheTargetAtFullSoc() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(40), forecast(0.0),
                REQUIRED_KWH, new PlanningPolicyConfig(20, 90, 10.0));

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(90);
    }

    @Test
    void letsTheBatteryDrainToMinimumWhenTheForecastCoversEverything() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(80), forecast(25.0),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.targetSocPercent()).isEqualTo(20);
    }

    @Test
    void chargesToFullWhenBatteryCapacityIsUnknown() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(50), forecast(14.5),
                REQUIRED_KWH, unknownCapacity);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(100);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
    }

    @Test
    void decidesTheSameWhateverTheBatteryHoldsAtDecisionTime() {
        // A decision made at 14:00 with a full battery must still protect the reserve: by the
        // time the window starts the battery may have drained. The inverter compares SOC with
        // the target itself, during the window.
        for (int soc : new int[] {30, 55, 60, 100}) {
            var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(soc), forecast(14.5),
                    REQUIRED_KWH, config);

            assertThat(decision.chargeFromGrid()).as("soc %d", soc).isTrue();
            assertThat(decision.targetSocPercent()).as("soc %d", soc).isEqualTo(55);
            assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
            assertThat(decision.reasonParams()).containsEntry("soc", (double) soc);
        }
    }

    @Test
    void keepsAFullReserveWhenNothingIsForecastEvenIfTheBatteryIsFullNow() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(100), forecast(0.0),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(100);
    }

    private ProductionForecast forecast(double expectedKwh) {
        return new ProductionForecast(LocalDate.of(2026, 1, 16), expectedKwh);
    }
}
