package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.DailyEnergyCounters;
import io.github.jaroslawdabrowski.energyplanning.domain.DecisionReason;
import io.github.jaroslawdabrowski.energyplanning.domain.DirectionPanelSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.InverterConnectionSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.NotificationSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.PlanningSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.RunIssue;
import io.github.jaroslawdabrowski.energyplanning.domain.ScheduleSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;
import io.github.jaroslawdabrowski.energyplanning.port.in.GuardChargeScheduleUseCase.Verification;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.NotificationPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.PlanningHistoryPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.RunIssuePort;
import io.github.jaroslawdabrowski.preferences.domain.Language;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleGuardTest {

    private final ChargeDecision decision = new ChargeDecision(Instant.parse("2026-01-15T20:45:00Z"),
            ChargeWindow.OVERNIGHT, true, 55, DecisionReason.FORECAST_INSUFFICIENT, Map.of());

    private final FakeInverter inverter = new FakeInverter();
    private final List<RunIssue> issues = new ArrayList<>();
    private final List<String> notifications = new ArrayList<>();
    private final ScheduleGuard guard = new ScheduleGuard(inverter, new OneDecision(decision), new FixedSettings(),
            new FixedClock(), new IssueReporter(new RecordingIssues(), new RecordingNotifications(), () -> Language.PL));

    @Test
    void isHappyWhenTheGovernedSlotsHoldTheDecision() {
        inverter.overnightSlots(true, 55);

        assertThat(guard.verify(ChargeWindow.OVERNIGHT)).isEqualTo(Verification.OK);
        assertThat(inverter.writes).isZero();
    }

    @Test
    void rewritesOnceWhenASlotReverted() {
        inverter.overnightSlots(true, 55);
        inverter.slots.set(5, slot(5, false, 20, ChargeWindow.OVERNIGHT));
        inverter.fixOnWrite = true;

        assertThat(guard.verify(ChargeWindow.OVERNIGHT)).isEqualTo(Verification.FIXED);
        assertThat(inverter.writes).isEqualTo(1);
        assertThat(issues).isEmpty();
    }

    @Test
    void reportsAMismatchThatSurvivesTheRewrite() {
        inverter.overnightSlots(false, 20);

        assertThat(guard.verify(ChargeWindow.OVERNIGHT)).isEqualTo(Verification.MISMATCH);
        assertThat(issues).singleElement().extracting(RunIssue::kind).isEqualTo(RunIssue.Kind.SCHEDULE_MISMATCH);
        assertThat(notifications).singleElement().asString().contains("falownik nie przyjął");
    }

    @Test
    void skipsTheCheckWhenWritesAreOff() {
        inverter.appliesWrites = false;
        inverter.overnightSlots(false, 20);

        assertThat(guard.verify(ChargeWindow.OVERNIGHT)).isEqualTo(Verification.SKIPPED);
    }

    @Test
    void reportsAFailedDecision() {
        guard.reportFailure(ChargeWindow.AFTERNOON, "connect timed out");

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.kind()).isEqualTo(RunIssue.Kind.DECISION_FAILED);
            assertThat(issue.window()).isEqualTo(ChargeWindow.AFTERNOON);
        });
        assertThat(notifications).singleElement().asString().contains("popołudniowa").contains("connect timed out");
    }

    private static TouScheduleSlot slot(int index, boolean grid, int target, ChargeWindow governedBy) {
        return new TouScheduleSlot(index, LocalTime.of(0, 0), LocalTime.of(1, 0), 5000, target, grid, governedBy);
    }

    private final class FakeInverter implements InverterPort {
        final List<TouScheduleSlot> slots = new ArrayList<>();
        boolean appliesWrites = true;
        boolean fixOnWrite;
        int writes;

        void overnightSlots(boolean grid, int target) {
            slots.clear();
            for (int i = 0; i < 6; i++) {
                ChargeWindow governed = i == 0 || i == 5 ? ChargeWindow.OVERNIGHT : i == 2 ? ChargeWindow.AFTERNOON : null;
                slots.add(governed == ChargeWindow.OVERNIGHT ? slot(i, grid, target, governed) : slot(i, false, 20, governed));
            }
        }

        @Override
        public BatteryStatus readBatteryStatus(InverterConnectionSettings connection) {
            return new BatteryStatus(50);
        }

        @Override
        public void applyChargeSchedule(ChargeSchedule schedule, InverterConnectionSettings connection) {
            writes++;
            if (fixOnWrite) {
                overnightSlots(schedule.gridChargeEnabled(), schedule.targetSocPercent());
            }
        }

        @Override
        public boolean appliesWrites() {
            return appliesWrites;
        }

        @Override
        public DailyEnergyCounters readDailyEnergy(InverterConnectionSettings connection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TouScheduleSlot> readTouSchedule(InverterConnectionSettings connection) {
            return List.copyOf(slots);
        }
    }

    private record OneDecision(ChargeDecision decision) implements PlanningHistoryPort {
        @Override
        public void record(ChargeDecision d) {
        }

        @Override
        public List<ChargeDecision> findBetween(Instant from, Instant to) {
            return List.of(decision);
        }

        @Override
        public Optional<ChargeDecision> findLatest() {
            return Optional.of(decision);
        }

        @Override
        public Optional<ChargeDecision> findLatest(ChargeWindow window) {
            return window == decision.window() ? Optional.of(decision) : Optional.empty();
        }
    }

    private static final class FixedSettings implements ProcessSettingsPort {
        @Override
        public Optional<ProcessSettings> load() {
            var none = new DirectionPanelSettings(0, 0);
            return Optional.of(new ProcessSettings(
                    new PlanningSettings(20, 100, 10, 5, 6, 1.2, List.of("22:00-06:00", "13:00-15:00")),
                    new ScheduleSettings(LocalTime.of(21, 45), LocalTime.of(12, 45)),
                    new ForecastSettings(52, 21, none, none, none, none),
                    new InverterConnectionSettings("192.0.2.1", 8899, 1L),
                    new NotificationSettings("https://ntfy.example/test")));
        }

        @Override
        public void save(ProcessSettings settings) {
        }
    }

    private static final class FixedClock implements ClockPort {
        @Override
        public Instant now() {
            return Instant.parse("2026-01-15T20:47:00Z");
        }

        @Override
        public LocalDateTime nowLocal() {
            return LocalDateTime.of(2026, 1, 15, 21, 47);
        }

        @Override
        public LocalDateTime toLocal(Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.ofHours(1));
        }
    }

    private final class RecordingIssues implements RunIssuePort {
        @Override
        public void record(RunIssue issue) {
            issues.add(issue);
        }

        @Override
        public Optional<RunIssue> findLatest() {
            return issues.isEmpty() ? Optional.empty() : Optional.of(issues.getLast());
        }
    }

    private final class RecordingNotifications implements NotificationPort {
        @Override
        public boolean send(String title, String message, Priority priority, NotificationSettings settings) {
            notifications.add(title + " | " + message);
            return true;
        }
    }
}
