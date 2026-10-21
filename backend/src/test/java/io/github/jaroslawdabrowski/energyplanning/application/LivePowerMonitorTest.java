package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.DailyEnergyCounters;
import io.github.jaroslawdabrowski.energyplanning.domain.DirectionPanelSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.InverterConnectionSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.LivePower;
import io.github.jaroslawdabrowski.energyplanning.domain.PlanningSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettingsNotConfiguredException;
import io.github.jaroslawdabrowski.energyplanning.domain.ScheduleSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LivePowerMonitorTest {

    private final CountingInverter inverter = new CountingInverter();
    private final MovableClock clock = new MovableClock();

    @Test
    void sharesAReadingForAFewSecondsSoPollingTabsDontQueueOnTheLogger() {
        var monitor = new LivePowerMonitor(new StoredSettings(Optional.of(settings())), inverter, clock);

        var first = monitor.livePower();
        clock.plusSeconds(4);
        var second = monitor.livePower();
        clock.plusSeconds(2);
        var third = monitor.livePower();

        assertThat(second).isSameAs(first);
        assertThat(third.readAt()).isAfter(first.readAt());
        assertThat(inverter.reads).isEqualTo(2);
    }

    @Test
    void needsSettings() {
        var monitor = new LivePowerMonitor(new StoredSettings(Optional.empty()), inverter, clock);

        assertThatThrownBy(monitor::livePower).isInstanceOf(ProcessSettingsNotConfiguredException.class);
        assertThat(inverter.reads).isZero();
    }

    private static ProcessSettings settings() {
        var none = new DirectionPanelSettings(0, 0);
        return new ProcessSettings(
                new PlanningSettings(20, 100, 10, 5, 6, 1.2, List.of("22:00-06:00", "13:00-15:00")),
                new ScheduleSettings(LocalTime.of(21, 45), LocalTime.of(12, 45), LocalTime.of(3, 30)),
                new ForecastSettings(52, 21, none, none, none, none),
                new InverterConnectionSettings("192.0.2.1", 8899, 1L), null);
    }

    private record StoredSettings(Optional<ProcessSettings> settings) implements ProcessSettingsPort {
        @Override
        public Optional<ProcessSettings> load() {
            return settings;
        }

        @Override
        public void save(ProcessSettings settings) {
        }
    }

    private static final class CountingInverter implements InverterPort {
        int reads;

        @Override
        public LivePower readLivePower(InverterConnectionSettings connection) {
            reads++;
            return new LivePower(List.of(1000, 500), 800, -400, -300, 60);
        }

        @Override
        public BatteryStatus readBatteryStatus(InverterConnectionSettings connection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyChargeSchedule(ChargeSchedule schedule, InverterConnectionSettings connection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean appliesWrites() {
            return false;
        }

        @Override
        public DailyEnergyCounters readDailyEnergy(InverterConnectionSettings connection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TouScheduleSlot> readTouSchedule(InverterConnectionSettings connection) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MovableClock implements ClockPort {
        private LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);

        void plusSeconds(long seconds) {
            now = now.plusSeconds(seconds);
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
}
