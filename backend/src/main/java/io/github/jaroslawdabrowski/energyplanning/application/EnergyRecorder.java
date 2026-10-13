package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.ConsumptionEstimator;
import io.github.jaroslawdabrowski.energyplanning.domain.ConsumptionEstimator.LearnedConsumption;
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

/**
 * Measures what the decisions were about. At every cheap window boundary it snapshots the
 * inverter's daily counters; when a boundary is the end of an expensive period, the
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
        var snapshot = new EnergySnapshot(now, inverterPort.readDailyEnergy(connection),
                inverterPort.readBatteryStatus(connection).socPercent());
        energyRecordPort.saveSnapshot(snapshot);

        var calendar = calendar(settings);
        for (ChargeWindow window : ChargeWindow.values()) {
            calendar.expensivePeriodEndingAt(calendar.windowFor(window), now).ifPresent(period -> {
                if (!period.from().toLocalDate().equals(period.to().toLocalDate())) {
                    LOG.infof("Not measuring %s period %s - it crosses the counters' midnight reset", window, period);
                    return;
                }
                energyRecordPort.findSnapshotNear(period.from(), SNAPSHOT_TOLERANCE).ifPresentOrElse(start -> {
                    var outcome = PeriodOutcome.between(window, start, snapshot);
                    energyRecordPort.saveOutcome(outcome);
                    LOG.infof("Measured %s period %s-%s: load=%.1f kWh, pv=%.1f kWh, gridImport=%.1f kWh",
                            window, period.from(), period.to(), outcome.loadKwh(), outcome.pvKwh(),
                            outcome.gridImportKwh());
                }, () -> LOG.infof("No snapshot from %s - %s period not measured", period.from(), window));
            });
        }
    }

    @Override
    public List<LocalTime> snapshotTimes(ProcessSettings settings) {
        return calendar(settings).boundaryTimes();
    }

    @Override
    public Optional<LearnedConsumption> learnedConsumption(ChargeWindow window) {
        var since = clockPort.nowLocal().minus(LEARNING_WINDOW);
        return ConsumptionEstimator.learn(energyRecordPort.findOutcomes(window, since));
    }

    private static TariffCalendar calendar(ProcessSettings settings) {
        return new TariffCalendar(TariffWindowConfigParser.parse(settings.planning().cheapTariffWindows()));
    }
}
