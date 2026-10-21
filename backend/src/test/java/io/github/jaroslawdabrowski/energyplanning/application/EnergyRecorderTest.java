package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.DailyEnergyCounters;
import io.github.jaroslawdabrowski.energyplanning.domain.DirectionPanelSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.EnergySnapshot;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.InverterConnectionSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.LivePower;
import io.github.jaroslawdabrowski.energyplanning.domain.PeriodOutcome;
import io.github.jaroslawdabrowski.energyplanning.domain.PlanningSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ScheduleSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.EnergyRecordPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyRecorderTest {

    private final FakeInverter inverter = new FakeInverter();
    private final InMemoryEnergyRecords records = new InMemoryEnergyRecords();
    private final FakeClock clock = new FakeClock();
    private final EnergyRecorder recorder = new EnergyRecorder(inverter, records, new FixedSettings(), clock);

    @Test
    void measuresTheMorningPeriodWhenTheAfternoonWindowStarts() {
        clock.at(LocalDateTime.of(2026, 1, 16, 6, 0, 20));
        inverter.counters = new DailyEnergyCounters(0.0, 2.0, 1.5, 0.0, 0.0, 0.5);
        inverter.soc = 60;
        recorder.recordSnapshot();
        assertThat(records.outcomes).isEmpty();

        clock.at(LocalDateTime.of(2026, 1, 16, 13, 0, 5));
        inverter.counters = new DailyEnergyCounters(3.0, 6.5, 1.9, 0.1, 0.4, 3.6);
        inverter.soc = 35;
        recorder.recordSnapshot();

        assertThat(records.outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.window()).isEqualTo(ChargeWindow.OVERNIGHT);
            assertThat(outcome.from()).isEqualTo(LocalDateTime.of(2026, 1, 16, 6, 0));
            assertThat(outcome.to()).isEqualTo(LocalDateTime.of(2026, 1, 16, 13, 0));
            assertThat(outcome.loadKwh()).isEqualTo(4.5);
            assertThat(outcome.socAtEndPercent()).isEqualTo(35);
        });
    }

    @Test
    void skipsAPeriodWhoseStartWasNeverSnapshotted() {
        clock.at(LocalDateTime.of(2026, 1, 16, 22, 0));
        inverter.counters = new DailyEnergyCounters(5.0, 12.0, 3.0, 0.2, 2.0, 4.0);
        recorder.recordSnapshot();

        assertThat(records.snapshots).hasSize(1);
        assertThat(records.outcomes).isEmpty();
    }

    @Test
    void takesSnapshotsEveryHourIncludingEveryWindowBoundary() {
        var times = recorder.snapshotTimes(new FixedSettings().load().orElseThrow());

        assertThat(times).hasSize(25).contains(LocalTime.of(23, 58), LocalTime.of(6, 0), LocalTime.of(13, 0), LocalTime.of(15, 0),
                LocalTime.of(22, 0));
    }

    private static final class FixedSettings implements ProcessSettingsPort {
        @Override
        public Optional<ProcessSettings> load() {
            var none = new DirectionPanelSettings(0, 0);
            return Optional.of(new ProcessSettings(
                    new PlanningSettings(20, 100, 10, 5, 6, 1.2, List.of("22:00-06:00", "13:00-15:00")),
                    new ScheduleSettings(LocalTime.of(21, 45), LocalTime.of(12, 45), LocalTime.of(3, 30)),
                    new ForecastSettings(52, 21, none, none, none, none),
                    new InverterConnectionSettings("192.0.2.1", 8899, 1L), null));
        }

        @Override
        public void save(ProcessSettings settings) {
        }
    }

    private static final class FakeInverter implements InverterPort {
        DailyEnergyCounters counters;
        int soc = 50;

        @Override
        public BatteryStatus readBatteryStatus(InverterConnectionSettings connection) {
            return new BatteryStatus(soc);
        }

        @Override
        public void applyChargeSchedule(ChargeSchedule schedule, InverterConnectionSettings connection) {
        }

        @Override
        public boolean appliesWrites() {
            return true;
        }

        @Override
        public DailyEnergyCounters readDailyEnergy(InverterConnectionSettings connection) {
            return counters;
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

    private static final class InMemoryEnergyRecords implements EnergyRecordPort {
        final List<EnergySnapshot> snapshots = new ArrayList<>();
        final List<PeriodOutcome> outcomes = new ArrayList<>();

        @Override
        public void saveSnapshot(EnergySnapshot snapshot) {
            snapshots.add(snapshot);
        }

        @Override
        public Optional<EnergySnapshot> findSnapshotNear(LocalDateTime at, Duration tolerance) {
            return snapshots.stream()
                    .filter(s -> Duration.between(at, s.takenAt()).abs().compareTo(tolerance) <= 0)
                    .min(Comparator.comparing(s -> Duration.between(at, s.takenAt()).abs()));
        }

        @Override
        public List<EnergySnapshot> findSnapshotsSince(LocalDateTime since) {
            return snapshots.stream().filter(s -> !s.takenAt().isBefore(since)).toList();
        }

        @Override
        public List<EnergySnapshot> findSnapshotsBetween(LocalDateTime from, LocalDateTime to) {
            return snapshots.stream().filter(s -> !s.takenAt().isBefore(from) && s.takenAt().isBefore(to)).toList();
        }

        @Override
        public void saveOutcome(PeriodOutcome outcome) {
            outcomes.removeIf(o -> o.window() == outcome.window() && o.from().equals(outcome.from()));
            outcomes.add(outcome);
        }

        @Override
        public List<PeriodOutcome> findOutcomes(ChargeWindow window, LocalDateTime since) {
            return outcomes.stream().filter(o -> o.window() == window && !o.from().isBefore(since)).toList();
        }

        @Override
        public List<PeriodOutcome> findOutcomesSince(LocalDateTime since) {
            return outcomes.stream().filter(o -> !o.from().isBefore(since)).toList();
        }
    }

    private static final class FakeClock implements ClockPort {
        private LocalDateTime now;

        void at(LocalDateTime now) {
            this.now = now;
        }

        @Override
        public Instant now() {
            return now.toInstant(ZoneOffset.UTC);
        }

        @Override
        public LocalDateTime nowLocal() {
            return now;
        }

        @Override
        public LocalDateTime toLocal(Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
    }

    @Test
    void skipsASnapshotRunningLongAfterItsTime() {
        // The machine woke at 08:20 and ran the missed jobs - those counters aren't 08:00's.
        clock.at(LocalDateTime.of(2026, 1, 16, 8, 20));
        inverter.counters = new DailyEnergyCounters(0.5, 3.9, 2.0, 0, 0, 1.0);
        recorder.recordSnapshot();

        assertThat(records.snapshots).isEmpty();
    }

    @Test
    void takesOnlyOneSnapshotWhenJobsFireTogether() {
        clock.at(LocalDateTime.of(2026, 1, 16, 9, 1));
        inverter.counters = new DailyEnergyCounters(1.0, 4.5, 2.0, 0, 0, 1.2);
        recorder.recordSnapshot();
        recorder.recordSnapshot();

        assertThat(records.snapshots).hasSize(1);
    }

    @Test
    void recoversAPeriodWhoseOutcomeWasNeverSaved() {
        // 2026-10-14: both snapshots made it, the outcome hit a locked database at 22:00.
        records.snapshots.add(new EnergySnapshot(LocalDateTime.of(2026, 1, 16, 15, 0),
                new DailyEnergyCounters(4.0, 6.0, 2.0, 0, 1.0, 1.0), 70));
        records.snapshots.add(new EnergySnapshot(LocalDateTime.of(2026, 1, 16, 22, 0),
                new DailyEnergyCounters(4.6, 11.2, 3.1, 0, 1.0, 4.9), 31));

        clock.at(LocalDateTime.of(2026, 1, 17, 11, 0));
        inverter.counters = new DailyEnergyCounters(1.0, 3.0, 1.0, 0, 0, 1.0);
        recorder.recordSnapshot();

        assertThat(records.outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.window()).isEqualTo(ChargeWindow.AFTERNOON);
            assertThat(outcome.from()).isEqualTo(LocalDateTime.of(2026, 1, 16, 15, 0));
            assertThat(outcome.loadKwh()).isCloseTo(5.2, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(outcome.socAtEndPercent()).isEqualTo(31);
        });
    }
}
