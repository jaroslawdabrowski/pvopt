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
    void skipsGridChargeWhenThePeriodNeedsNothingFromTheBattery() {
        var decision = policy.decide(now, ChargeWindow.AFTERNOON, new BatteryStatus(50), balance(20.0), config);

        assertThat(decision.chargeFromGrid()).isFalse();
        assertThat(decision.window()).isEqualTo(ChargeWindow.AFTERNOON);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_SUFFICIENT);
        assertThat(decision.targetSocPercent()).isEqualTo(20);
    }

    @Test
    void fillsTheBatteryWhenNoSurplusIsForecast() {
        // An overcast November morning: whatever is left at 13:00 only saves the afternoon charge.
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(30), balance(14.5), config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(100);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FILL_LEAVING_SOLAR_ROOM);
        assertThat(decision.reasonParams()).containsEntry("targetSoc", 100.0).containsEntry("solarRoomKwh", 0.0)
                .containsEntry("reserveKwh", 3.5);
    }

    @Test
    void leavesRoomForPartOfTheForecastSurplus() {
        // A sunny morning (like 2026-11-08): 1.5 kWh short by 08:00, then the panels push the
        // battery 2.5 kWh above where it started. 60% of that is kept free: 1.5 kWh -> 85%.
        var sunny = PeriodBalance.of(
                Collections.nCopies(7, 1.0),
                List.of(0.0, 0.5, 1.0, 2.0, 2.0, 2.0, 2.0),
                Collections.nCopies(7, 1.0));
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(60), sunny, config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(85);
        assertThat(decision.reasonParams()).containsEntry("solarRoomKwh", 1.5).containsEntry("reserveKwh", 1.5);
    }

    @Test
    void neverLeavesSoMuchRoomThatTheReserveIsMissing() {
        // The sun ends 4 kWh above the start - room for 2.4 kWh would mean 76% - but the 6 kWh
        // the house needs before it comes still has to be there: 20% + 60%.
        var lateSun = PeriodBalance.of(
                List.of(2.0, 2.0, 2.0, 1.0, 1.0),
                List.of(0.0, 0.0, 0.0, 6.0, 6.0),
                Collections.nCopies(5, 1.0));
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(60), lateSun, config);

        assertThat(decision.targetSocPercent()).isEqualTo(80);
    }

    @Test
    void keepsOnlyTheReserveWhenTheSunWouldFillTheWholeBattery() {
        // 0.1 kWh short in the first hour, then a surplus bigger than the battery.
        var summer = PeriodBalance.of(
                List.of(0.5, 0.5, 0.5, 0.5),
                List.of(0.4, 6.0, 6.0, 6.0),
                Collections.nCopies(4, 1.0));
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(60), summer, config);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(21);
    }

    @Test
    void capsTheTargetAtFullSoc() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(40), balance(0.0), new PlanningPolicyConfig(20, 90, 10.0));

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(90);
    }

    @Test
    void chargesToFullWhenBatteryCapacityIsUnknown() {
        var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(50), balance(14.5), unknownCapacity);

        assertThat(decision.chargeFromGrid()).isTrue();
        assertThat(decision.targetSocPercent()).isEqualTo(100);
        assertThat(decision.reason()).isEqualTo(DecisionReason.FILL_LEAVING_SOLAR_ROOM);
    }

    @Test
    void decidesTheSameWhateverTheBatteryHoldsAtDecisionTime() {
        // A decision made at 14:00 with a full battery must still protect the expensive hours: by
        // the time the window starts the battery may have drained. The inverter compares SOC with
        // the target itself, during the window.
        for (int soc : new int[] {30, 55, 60, 100}) {
            var decision = policy.decide(now, ChargeWindow.OVERNIGHT, new BatteryStatus(soc), balance(14.5), config);

            assertThat(decision.chargeFromGrid()).as("soc %d", soc).isTrue();
            assertThat(decision.targetSocPercent()).as("soc %d", soc).isEqualTo(100);
            assertThat(decision.reasonParams()).containsEntry("soc", (double) soc);
        }
    }

    @Test
    void theTargetRoundsTheRoomDownAndTheReserveUp() {
        assertThat(ChargeDecisionPolicy.targetSocFor(0.31, 0.0, config)).isEqualTo(100);
        assertThat(ChargeDecisionPolicy.targetSocFor(0.0, 2.45, config)).isEqualTo(76);
        assertThat(ChargeDecisionPolicy.targetSocFor(7.01, 9.0, config)).isEqualTo(91);
    }

    /** A flat period: the reserve is simply what the forecast doesn't cover. */
    private static PeriodBalance balance(double forecastKwh) {
        return PeriodBalance.ofTotals(REQUIRED_KWH, forecastKwh);
    }
}
