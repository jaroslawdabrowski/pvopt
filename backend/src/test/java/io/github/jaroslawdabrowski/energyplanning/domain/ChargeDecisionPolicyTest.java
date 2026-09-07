package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeDecisionPolicyTest {

    private final ChargeDecisionPolicy policy = new ChargeDecisionPolicy();
    private final Instant now = Instant.parse("2026-01-15T22:30:00Z");
    private final PlanningPolicyConfig config = new PlanningPolicyConfig(20, 100, 15.0, 1.2);

    @Test
    void doesNotChargeOutsideCheapWindow() {
        var decision = policy.decide(now, new BatteryStatus(50), TariffRate.EXPENSIVE,
                forecast(5.0), config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.reason()).isEqualTo(DecisionReason.OUTSIDE_CHEAP_WINDOW);
    }

    @Test
    void doesNotChargeWhenAlreadyFull() {
        var decision = policy.decide(now, new BatteryStatus(100), TariffRate.CHEAP,
                forecast(0.0), config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.reason()).isEqualTo(DecisionReason.ALREADY_AT_TARGET_SOC);
    }

    @Test
    void chargesToMinimumWhenBelowSafetyFloorRegardlessOfForecast() {
        var decision = policy.decide(now, new BatteryStatus(10), TariffRate.CHEAP,
                forecast(50.0), config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(20);
        assertThat(decision.reason()).isEqualTo(DecisionReason.BELOW_MIN_SOC);
    }

    @Test
    void skipsGridChargeWhenTomorrowForecastIsSufficient() {
        // required: 15.0 * 1.2 = 18.0 kWh
        var decision = policy.decide(now, new BatteryStatus(50), TariffRate.CHEAP,
                forecast(20.0), config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_SUFFICIENT);
    }

    @Test
    void chargesToFullWhenTomorrowForecastIsInsufficient() {
        var decision = policy.decide(now, new BatteryStatus(50), TariffRate.CHEAP,
                forecast(5.0), config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(100);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
    }

    private ProductionForecast forecast(double expectedKwh) {
        return new ProductionForecast(LocalDate.of(2026, 1, 16), expectedKwh);
    }
}
