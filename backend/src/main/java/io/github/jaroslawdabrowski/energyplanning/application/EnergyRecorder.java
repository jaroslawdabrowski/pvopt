package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.ConsumptionEstimator;
import io.github.jaroslawdabrowski.energyplanning.domain.ConsumptionLearning;
import io.github.jaroslawdabrowski.energyplanning.domain.EnergySnapshot;
import io.github.jaroslawdabrowski.energyplanning.domain.PeriodOutcome;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettingsNotConfiguredException;
import io.github.jaroslawdabrowski.energyplanning.domain.TariffCalendar;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetLearnedConsumptionUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.RecordEnergySnapshotUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.EnergyRecordPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Measures what the decisions were about. Every full hour (and at every cheap window
 * boundary) it snapshots the inverter's daily counters - the hourly ones give the daily
 * consumption shape; when a boundary is the end of an expensive period, the
 * snapshot from the period's start is subtracted to get that period's outcome (load, PV,
 * grid import, SOC). Recent outcomes then replace the hand-entered consumption estimates.
 *
 * The counters reset at the inverter's midnight, so a period crossing midnight can't be
 * measured this way - G12's expensive periods never do.
 */
@ApplicationScoped
public class EnergyRecorder implements RecordEnergySnapshotUseCase, GetLearnedConsumptionUseCase {

    private static final Logger LOG = Logger.getLogger(EnergyRecorder.class);

    /** How far back measured periods count towards the learned consumption. */
    static final Duration LEARNING_WINDOW = Duration.ofDays(14);
    /** A scheduled snapshot may land a little late; the period start must still be found. */
    private static final Duration SNAPSHOT_TOLERANCE = Duration.ofMinutes(15);
    /** How late a snapshot may still be taken for its time - and how close two may be. */
    static final Duration SNAPSHOT_LATENESS = Duration.ofMinutes(5);
    /** How far back a period without an outcome is still measured from its snapshots. */
    private static final Duration BACKFILL_WINDOW = Duration.ofDays(3);

    private final InverterPort inverterPort;
    private final EnergyRecordPort energyRecordPort;
    private final ProcessSettingsPort settingsPort;
    private final ClockPort clockPort;

    public EnergyRecorder(InverterPort inverterPort, EnergyRecordPort energyRecordPort,
            ProcessSettingsPort settingsPort, ClockPort clockPort) {
        this.inverterPort = inverterPort;
        this.energyRecordPort = energyRecordPort;
        this.settingsPort = settingsPort;
        this.clockPort = clockPort;
    }

    @Override
    public void recordSnapshot() {
        var settings = settingsPort.load().orElseThrow(ProcessSettingsNotConfiguredException::new);
        var connection = settings.inverterConnection();
        // Truncated to the minute: the job fires on the boundary, and the period math compares
        // exact times (06:00, 13:00, ...).
        LocalDateTime now = clockPort.nowLocal().truncatedTo(ChronoUnit.MINUTES);
        // A job that fires long after its time (the machine was asleep and runs the missed ones
        // on waking) would stamp counters at 08:04 as if they were 06:00's - skip it, and skip a
        // second job firing in the same burst.
        var due = latestSnapshotTimeAtOrBefore(now, settings);
        if (Duration.between(due, now).compareTo(SNAPSHOT_LATENESS) > 0) {
            LOG.infof("Energy snapshot for %s skipped - running at %s, too late to stand for it", due, now);
            return;
        }
        if (energyRecordPort.findSnapshotNear(now, SNAPSHOT_LATENESS).isPresent()) {
            LOG.infof("Energy snapshot at %s skipped - one was already taken", now);
            return;
        }
        var snapshot = new EnergySnapshot(now, inverterPort.readDailyEnergy(connection),
                inverterPort.readBatteryStatus(connection).socPercent());
        energyRecordPort.saveSnapshot(snapshot);

        measureCompletedPeriods(settings, now);
    }

    /**
     * Records the outcome of every period of the last few days that has ended, has snapshots
     * at both ends and no outcome yet - normally just the one ending now, but it also recovers
     * a period whose outcome failed to save (the 2026-10-14 22:00 one hit a locked database)
     * once both snapshots are there.
     */
    private void measureCompletedPeriods(ProcessSettings settings, LocalDateTime now) {
        var calendar = calendar(settings);
        for (ChargeWindow window : ChargeWindow.values()) {
            var tariffWindow = calendar.windowFor(window);
            var measured = energyRecordPort.findOutcomes(window, now.minus(BACKFILL_WINDOW)).stream()
                    .map(PeriodOutcome::from)
                    .collect(Collectors.toSet());
            var period = calendar.expensivePeriodAfter(tariffWindow, now.minus(BACKFILL_WINDOW));
            while (!period.to().isAfter(now)) {
                if (!measured.contains(period.from()) && period.from().toLocalDate().equals(period.to().toLocalDate())) {
                    measure(window, period.from(), period.to());
                }
                period = calendar.expensivePeriodAfter(tariffWindow, period.from());
            }
        }
    }

    private void measure(ChargeWindow window, LocalDateTime from, LocalDateTime to) {
        var start = energyRecordPort.findSnapshotNear(from, SNAPSHOT_TOLERANCE);
        var end = energyRecordPort.findSnapshotNear(to, SNAPSHOT_TOLERANCE);
        if (start.isEmpty() || end.isEmpty()) {
            return;
        }
        var outcome = PeriodOutcome.between(window, start.get(), end.get());
        energyRecordPort.saveOutcome(outcome);
        LOG.infof("Measured %s period %s-%s: load=%.1f kWh, pv=%.1f kWh, gridImport=%.1f kWh",
                window, from, to, outcome.loadKwh(), outcome.pvKwh(), outcome.gridImportKwh());
    }

    private LocalDateTime latestSnapshotTimeAtOrBefore(LocalDateTime now, ProcessSettings settings) {
        LocalDateTime latest = null;
        for (LocalTime time : snapshotTimes(settings)) {
            LocalDateTime candidate = now.toLocalDate().atTime(time);
            if (candidate.isAfter(now)) {
                candidate = candidate.minusDays(1);
            }
            if (latest == null || candidate.isAfter(latest)) {
                latest = candidate;
            }
        }
        return latest;
    }

    @Override
    public List<LocalTime> snapshotTimes(ProcessSettings settings) {
        // Every full hour (the hourly consumption shape) plus any window boundary that isn't one.
        var times = new TreeSet<LocalTime>();
        for (int hour = 0; hour < 24; hour++) {
            times.add(LocalTime.of(hour, 0));
        }
        times.addAll(calendar(settings).boundaryTimes());
        return List.copyOf(times);
    }

    @Override
    public ConsumptionLearning learnedConsumption(ChargeWindow window) {
        var settings = settingsPort.load().orElseThrow(ProcessSettingsNotConfiguredException::new);
        var now = clockPort.nowLocal();
        var from = settings.planning().learnFrom(window, now, LEARNING_WINDOW);
        var outcomes = energyRecordPort.findOutcomes(window, from);
        var countingFrom = settings.planning().consumptionChangedAt(window).filter(from::equals);
        return new ConsumptionLearning(ConsumptionEstimator.learn(outcomes), outcomes.size(), countingFrom);
    }

    private static TariffCalendar calendar(ProcessSettings settings) {
        return new TariffCalendar(TariffWindowConfigParser.parse(settings.planning().cheapTariffWindows()));
    }
}
