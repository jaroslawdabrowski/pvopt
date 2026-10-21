package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.DailyEnergyCounters;
import io.github.jaroslawdabrowski.energyplanning.domain.DecisionReason;
import io.github.jaroslawdabrowski.energyplanning.domain.DirectionPanelSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.EnergySnapshot;
import io.github.jaroslawdabrowski.energyplanning.domain.ExpensivePeriod;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.InverterConnectionSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.LivePower;
import io.github.jaroslawdabrowski.energyplanning.domain.NotificationSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.PeriodOutcome;
import io.github.jaroslawdabrowski.energyplanning.domain.PlanningSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionProfile;
import io.github.jaroslawdabrowski.energyplanning.domain.RunIssue;
import io.github.jaroslawdabrowski.energyplanning.domain.ScheduleSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.EnergyRecordPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.NotificationPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.PlanningHistoryPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.RunIssuePort;
import io.github.jaroslawdabrowski.preferences.domain.Language;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole overnight decision on fakes - the clock is UTC so local and instant times line up. */
class EnergyPlannerTest {

    private final LocalDateTime evening = LocalDateTime.of(2026, 10, 13, 21, 45);
    private final List<ChargeDecision> history = new ArrayList<>();
    private final List<PeriodOutcome> outcomes = new ArrayList<>();
    private final List<ChargeSchedule> written = new ArrayList<>();
    /** Tomorrow morning, 06-13: the sun only catches up after 09:00 (4.9 kWh in total). */
    private List<Double> forecastPerHour = List.of(0.0, 0.3, 0.59, 0.83, 0.99, 1.08, 1.1);

    private final EnergyPlanner planner = new EnergyPlanner(new FakeInverter(), new FakeForecast(), new History(),
            new FixedClock(), new FixedSettings(), new Records(),
            new IssueReporter(new NoIssues(), new NoNotifications(), () -> Language.PL), new NoIssues());

    @Test
    void reservesForTheMorningDeficitBeforeTheSunCatchesUp() {
        var decision = planner.planOvernightCharge();

        // 4.7 kWh x 1.2 spread flat over 7 hours against the forecast above: ~1.53 kWh short by 09:00.
        assertThat(decision.reason()).isEqualTo(DecisionReason.FORECAST_INSUFFICIENT);
        assertThat(decision.targetSocPercent()).isEqualTo(36);
        assertThat(decision.reasonParams()).containsEntry("reservePeakHour", 9.0).containsEntry("calibrationFactor", 1.0);
        assertThat(written).singleElement().isEqualTo(new ChargeSchedule(ChargeWindow.OVERNIGHT, true, 36));
    }

    @Test
    void scalesTheForecastByHowFarItHasBeenOff() {
        // Three past mornings where PV made half of what was forecast.
        for (int day = 1; day <= 3; day++) {
            var decidedAt = evening.minusDays(day + 1);
            history.add(new ChargeDecision(decidedAt.toInstant(ZoneOffset.UTC), ChargeWindow.OVERNIGHT, false, 20,
                    DecisionReason.FORECAST_SUFFICIENT, Map.of("periodFromHour", 6.0, "forecastRawKwh", 6.0)));
            var morning = decidedAt.toLocalDate().plusDays(1);
            outcomes.add(new PeriodOutcome(ChargeWindow.OVERNIGHT, morning.atTime(6, 0), morning.atTime(13, 0),
                    4.5, 3.0, 1.0, 0, 50, 25));
        }

        var decision = planner.planOvernightCharge();

        assertThat(decision.reasonParams()).containsEntry("calibrationFactor", 0.5).containsEntry("calibrationSamples", 3.0)
                .containsEntry("forecastRawKwh", 4.9).containsEntry("forecastKwh", 2.4);
        assertThat(decision.targetSocPercent()).isGreaterThan(36);
    }

    private final class FakeInverter implements InverterPort {
        @Override
        public BatteryStatus readBatteryStatus(InverterConnectionSettings connection) {
            return new BatteryStatus(60);
        }

        @Override
        public void applyChargeSchedule(ChargeSchedule schedule, InverterConnectionSettings connection) {
            written.add(schedule);
        }

        @Override
        public boolean appliesWrites() {
            return true;
        }

        @Override
        public DailyEnergyCounters readDailyEnergy(InverterConnectionSettings connection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LivePower readLivePower(InverterConnectionSettings connection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TouScheduleSlot> readTouSchedule(InverterConnectionSettings connection) {
            return List.of();
        }
    }

    private final class FakeForecast implements ForecastPort {
        @Override
        public ProductionProfile productionProfile(ExpensivePeriod period, ForecastSettings settings) {
            return new ProductionProfile(period.from(), forecastPerHour, Collections.nCopies(7, 1.0));
        }
    }

    private final class History implements PlanningHistoryPort {
        @Override
        public void record(ChargeDecision decision) {
            history.add(decision);
        }

        @Override
        public List<ChargeDecision> findBetween(Instant from, Instant to) {
            return history.stream().filter(d -> !d.decidedAt().isBefore(from) && !d.decidedAt().isAfter(to)).toList();
        }

        @Override
        public Optional<ChargeDecision> findLatest() {
            return history.isEmpty() ? Optional.empty() : Optional.of(history.getLast());
        }

        @Override
        public Optional<ChargeDecision> findLatest(ChargeWindow window) {
            return history.stream().filter(d -> d.window() == window).reduce((a, b) -> b);
        }
    }

    private final class Records implements EnergyRecordPort {
        @Override
        public void saveSnapshot(EnergySnapshot snapshot) {
        }

        @Override
        public Optional<EnergySnapshot> findSnapshotNear(LocalDateTime at, Duration tolerance) {
            return Optional.empty();
        }

        @Override
        public List<EnergySnapshot> findSnapshotsSince(LocalDateTime since) {
            return List.of();
        }

        @Override
        public List<EnergySnapshot> findSnapshotsBetween(LocalDateTime from, LocalDateTime to) {
            return List.of();
        }

        @Override
        public void saveOutcome(PeriodOutcome outcome) {
        }

        @Override
        public List<PeriodOutcome> findOutcomes(ChargeWindow window, LocalDateTime since) {
            // Kept empty on purpose: the consumption estimate stays the settings' 4.7 kWh.
            return List.of();
        }

        @Override
        public List<PeriodOutcome> findOutcomesSince(LocalDateTime since) {
            return outcomes.stream().filter(o -> !o.from().isBefore(since)).toList();
        }
    }

    private final class FixedClock implements ClockPort {
        @Override
        public Instant now() {
            return evening.toInstant(ZoneOffset.UTC);
        }

        @Override
        public LocalDateTime nowLocal() {
            return evening;
        }

        @Override
        public LocalDateTime toLocal(Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
    }

    private static final class FixedSettings implements ProcessSettingsPort {
        @Override
        public Optional<ProcessSettings> load() {
            var none = new DirectionPanelSettings(0, 0);
            return Optional.of(new ProcessSettings(
                    new PlanningSettings(20, 100, 10, 4.7, 5.1, 1.2, List.of("22:00-06:00", "13:00-15:00")),
                    new ScheduleSettings(LocalTime.of(21, 45), LocalTime.of(12, 45), LocalTime.of(3, 30)),
                    new ForecastSettings(52, 21, none, new DirectionPanelSettings(30, 3), none,
                            new DirectionPanelSettings(30, 5)),
                    new InverterConnectionSettings("192.0.2.1", 8899, 1L),
                    NotificationSettings.NONE));
        }

        @Override
        public void save(ProcessSettings settings) {
        }
    }

    private static final class NoIssues implements RunIssuePort {
        @Override
        public void record(RunIssue issue) {
        }

        @Override
        public Optional<RunIssue> findLatest() {
            return Optional.empty();
        }
    }

    private static final class NoNotifications implements NotificationPort {
        @Override
        public boolean send(String title, String message, Priority priority, NotificationSettings settings) {
            return false;
        }
    }
}
