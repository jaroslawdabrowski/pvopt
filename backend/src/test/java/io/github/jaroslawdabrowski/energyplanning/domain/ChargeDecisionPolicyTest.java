package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeDecisionPolicyTest {

    private final ChargeDecisionPolicy policy = new ChargeDecisionPolicy();
    private final Instant now = Instant.parse("2026-01-15T22:30:00Z");
    private final PlanningPolicyConfig config = new PlanningPolicyConfig(20, 100);
    private static final double REQUIRED_KWH = 18.0;

    @Test
    void doesNotChargeWhenAlreadyFull() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(100), forecast(0.0),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.window()).isEqualTo(ChargeWindow.OVERNIGHT);
        assertThat(decision.reason()).isEqualTo(DecisionReason.ALREADY_AT_TARGET_SOC);
        // The inverter enforces target-SOC as a floor regardless of the grid-charge-enable
        // flag - writing the battery's current SOC here would make it top up from the grid
        // the moment SOC drifts down, even with charging nominally off.
        assertThat(decision.targetSocPercent()).isEqualTo(20);
    }

    @Test
    void chargesToMinimumWhenBelowSafetyFloorRegardlessOfForecast() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(10), forecast(50.0),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(20);
        assertThat(decision.reason()).isEqualTo(DecisionReason.BELOW_MIN_SOC);
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
    void chargesToFullWhenForecastIsInsufficient() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(50), forecast(5.0),
                REQUIRED_KWH, config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(100);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
    }

    private ProductionForecast forecast(double expectedKwh) {
        return new ProductionForecast(LocalDate.of(2026, 1, 16), expectedKwh);
    }
}
