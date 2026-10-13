package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

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
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(10), balance(50.0), config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(20);
        assertThat(decision.reason()).isEqualTo(DecisionReason.BELOW_MIN_SOC);
    }

    @Test
    void chargesForTheShortfallWhenBelowSafetyFloorAndForecastFallsShort() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(10), balance(15.0), config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(50);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
    }

    @Test
    void skipsGridChargeWhenForecastIsSufficient() {
        var decision = policy.decide(now, ChargeWindow.AFTERNOON, new BatteryStatus(50), balance(20.0), config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.window()).isEqualTo(ChargeWindow.AFTERNOON);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_SUFFICIENT);
        assertThat(decision.targetSocPercent()).isEqualTo(20);
    }

    @Test
    void chargesOnlyForTheEnergyTheForecastWontCover() {
        // 18 kWh needed, 14.5 kWh forecast -> 3.5 kWh short -> 35 points above the 20% minimum.
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(30), balance(14.5), config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(55);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
        assertThat(decision.reasonParams()).containsEntry("targetSoc", 55.0);
    }

    @Test
    void roundsTheShortfallUpToAWholePercent() {
        // 0.31 kWh short on a 10 kWh battery is 3.1 points - charge to 24%, not 23%.
        var decision = policy.decide(now, ChargeWindow.AFTERNOON, new BatteryStatus(20), balance(17.69), config);

        assertThat(decision.targetSocPercent()).isEqualTo(24);
    }

    @Test
    void capsTheTargetAtFullSoc() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(40), balance(0.0), new PlanningPolicyConfig(20, 90, 10.0));

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(90);
    }

    @Test
    void letsTheBatteryDrainToMinimumWhenTheForecastCoversEverything() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(80), balance(25.0), config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.targetSocPercent()).isEqualTo(20);
    }

    @Test
    void chargesToFullWhenBatteryCapacityIsUnknown() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(50), balance(14.5), unknownCapacity);

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
            var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(soc), balance(14.5), config);

            assertThat(decision.chargeFromGrid()).as("soc %d", soc).isTrue();
            assertThat(decision.targetSocPercent()).as("soc %d", soc).isEqualTo(55);
            assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
            assertThat(decision.reasonParams()).containsEntry("soc", (double) soc);
        }
    }

    @Test
    void keepsAFullReserveWhenNothingIsForecastEvenIfTheBatteryIsFullNow() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(100), balance(0.0), config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(100);
    }

    @Test
    void sizesTheReserveToTheRunningDeficitNotThePeriodTotal() {
        // Morning: 5.6 kWh needed, 4.9 kWh of PV - but the sun arrives after 08:00, so the
        // battery has to carry ~1.5 kWh first. 1.53 kWh on 10 kWh -> 16 points over 20%.
        var morning = PeriodBalance.of(
                Collections.nCopies(7, 0.806),
                List.of(0.0, 0.3, 0.59, 0.83, 0.99, 1.08, 1.1),
                Collections.nCopies(7, 1.0));
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(60), morning, config);

        assertThat(decision.targetSocPercent()).isEqualTo(36);
        assertThat(decision.reasonParams()).containsEntry("reserveKwh", 1.5);
    }

    /** A flat period: the reserve is simply what the forecast doesn't cover. */
    private static PeriodBalance balance(double forecastKwh) {
        return PeriodBalance.ofTotals(REQUIRED_KWH, forecastKwh);
    }
}
