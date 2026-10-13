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
    void holdsTheNeededReserveWhenAlreadyFull() {
        // 18 kWh needed, nothing forecast -> capped at 100%; the battery is there already.
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(100), forecast(0.0),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.window()).isEqualTo(ChargeWindow.OVERNIGHT);
        assertThat(decision.reason()).isEqualTo(DecisionReason.RESERVE_HELD);
        assertThat(decision.targetSocPercent()).isEqualTo(100);
    }

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
    void holdsTheReserveThroughTheCheapWindowWhenTheBatteryAlreadyHasIt() {
        // 3.5 kWh short -> 55% needed, battery is at 60%: no grid charge, but the floor stays
        // at 55% so cheap-hour load doesn't eat what the expensive hours need.
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(60), forecast(14.5),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.reason()).isEqualTo(DecisionReason.RESERVE_HELD);
        assertThat(decision.targetSocPercent()).isEqualTo(55);
        assertThat(decision.reasonParams()).containsEntry("soc", 60.0).containsEntry("targetSoc", 55.0);
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
    void doesNotHoldAFullBatteryWhenCapacityIsUnknown() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(100), forecast(14.5),
                REQUIRED_KWH, unknownCapacity);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.reason()).isEqualTo(DecisionReason.ALREADY_AT_TARGET_SOC);
        assertThat(decision.targetSocPercent()).isEqualTo(20);
    }

    private ProductionForecast forecast(double expectedKwh) {
        return new ProductionForecast(LocalDate.of(2026, 1, 16), expectedKwh);
    }
}
