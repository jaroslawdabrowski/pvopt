package io.github.jaroslawdabrowski.energyplanning.adapter.in.scheduler;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettingsNotConfiguredException;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetProcessSettingsUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GuardChargeScheduleUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanAfternoonTopUpUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanOvernightChargeUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.RecordEnergySnapshotUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.SchedulingPort;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * G12 has exactly two cheap windows, so exactly two decisions are needed per day - not
 * hourly. Each job runs shortly before its window starts, leaving enough time for the
 * Modbus write to land before the inverter's own TOU slot takes over.
 *
 * Unlike a fixed {@code @Scheduled} cron, the job times come from user-editable
 * {@link ProcessSettings} and can change at any time, so this adapter uses Quarkus's
 * programmatic {@link Scheduler} API instead: jobs are (re-)registered on startup and
 * again whenever settings are saved (see {@link #reschedule}). It plays both an "in"
 * role (its jobs invoke the planning use cases) and the "out" {@link SchedulingPort}
 * role (the application layer calls back into it to reconfigure) - a common, pragmatic
 * exception to one-direction hexagonal wiring for scheduler adapters.
 */
@ApplicationScoped
public class DynamicEnergyPlanningScheduler implements SchedulingPort {

    private static final Logger LOG = Logger.getLogger(DynamicEnergyPlanningScheduler.class);

    private static final String OVERNIGHT_JOB = "overnight-charge";
    private static final String OVERNIGHT_RECHECK_JOB = "overnight-recheck";
    private static final String AFTERNOON_JOB = "afternoon-topup";

    static final int MAX_ATTEMPTS = 3;
    static final Duration RETRY_DELAY = Duration.ofMinutes(3);
    /** Long enough for the device to settle - the register-159 revert showed up within minutes, not seconds. */
    static final Duration VERIFY_DELAY = Duration.ofMinutes(2);

    private final Scheduler scheduler;
    private final PlanOvernightChargeUseCase planOvernightChargeUseCase;
    private final PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase;
    private final GetProcessSettingsUseCase getProcessSettingsUseCase;
    private final RecordEnergySnapshotUseCase recordEnergySnapshotUseCase;
    private final GuardChargeScheduleUseCase guardUseCase;
    private final ScheduledExecutorService delayed = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofPlatform().name("planning-retry").daemon().unstarted(runnable));
    /** Snapshot jobs are one per boundary time, so their identities change with the tariff windows. */
    private final Set<String> snapshotJobs = new HashSet<>();

    public DynamicEnergyPlanningScheduler(Scheduler scheduler, PlanOvernightChargeUseCase planOvernightChargeUseCase,
            PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase, GetProcessSettingsUseCase getProcessSettingsUseCase,
            RecordEnergySnapshotUseCase recordEnergySnapshotUseCase, GuardChargeScheduleUseCase guardUseCase) {
        this.scheduler = scheduler;
        this.planOvernightChargeUseCase = planOvernightChargeUseCase;
        this.planAfternoonTopUpUseCase = planAfternoonTopUpUseCase;
        this.getProcessSettingsUseCase = getProcessSettingsUseCase;
        this.recordEnergySnapshotUseCase = recordEnergySnapshotUseCase;
        this.guardUseCase = guardUseCase;
    }

    void onStart(@Observes StartupEvent event) {
        reschedule(getProcessSettingsUseCase.currentSettings());
    }

    @Override
    public void reschedule(Optional<ProcessSettings> settings) {
        unscheduleIfPresent(OVERNIGHT_JOB);
        unscheduleIfPresent(OVERNIGHT_RECHECK_JOB);
        unscheduleIfPresent(AFTERNOON_JOB);
        snapshotJobs.forEach(this::unscheduleIfPresent);
        snapshotJobs.clear();

        settings.ifPresentOrElse(s -> {
            scheduleDaily(OVERNIGHT_JOB, s.schedule().overnightTime(), this::runOvernight);
            // Same decision again with a fresher forecast - it simply overwrites the 21:45 one.
            s.schedule().overnightRecheck().ifPresent(time -> scheduleDaily(OVERNIGHT_RECHECK_JOB, time, this::runOvernight));
            scheduleDaily(AFTERNOON_JOB, s.schedule().afternoonTime(), this::runAfternoon);
            var snapshotTimes = recordEnergySnapshotUseCase.snapshotTimes(s);
            for (LocalTime time : snapshotTimes) {
                String identity = "energy-snapshot-" + time;
                scheduleDaily(identity, time, this::runSnapshot);
                snapshotJobs.add(identity);
            }
            LOG.infof("Scheduled jobs registered: overnight=%s (recheck %s), afternoon=%s, %d energy snapshots",
                    s.schedule().overnightTime(), s.schedule().overnightRecheck().map(LocalTime::toString).orElse("off"),
                    s.schedule().afternoonTime(), snapshotTimes.size());
        }, () -> LOG.info("No process settings configured - scheduled jobs are disabled until settings are saved"));
    }

    private void unscheduleIfPresent(String identity) {
        if (scheduler.getScheduledJob(identity) != null) {
            scheduler.unscheduleJob(identity);
        }
    }

    private void scheduleDaily(String identity, LocalTime time, Runnable task) {
        String cron = "0 %d %d * * ?".formatted(time.getMinute(), time.getHour());
        scheduler.newJob(identity).setCron(cron).setTask(execution -> task.run()).schedule();
    }

    private void runOvernight() {
        attemptDecision(ChargeWindow.OVERNIGHT, 1);
    }

    private void runAfternoon() {
        attemptDecision(ChargeWindow.AFTERNOON, 1);
    }

    /**
     * A decision that fails (logger unreachable, a dropped Modbus frame) is retried a few minutes
     * later - the jobs run ~15 minutes before their window, so there's room for
     * {@link #MAX_ATTEMPTS} tries. Only when all of them fail is someone told; the inverter then
     * keeps its previous schedule. A decision that did go through is read back a little later.
     */
    private void attemptDecision(ChargeWindow window, int attempt) {
        try {
            var decision = window == ChargeWindow.OVERNIGHT
                    ? planOvernightChargeUseCase.planOvernightCharge()
                    : planAfternoonTopUpUseCase.planAfternoonTopUp();
            LOG.infof("%s planning decision (attempt %d): chargeFromGrid=%s, targetSoc=%d%%, reason=%s, params=%s",
                    window, attempt, decision.chargeFromGrid(), decision.targetSocPercent(), decision.reason(),
                    decision.reasonParams());
            later(VERIFY_DELAY, () -> verify(window));
        } catch (ProcessSettingsNotConfiguredException e) {
            LOG.infof("%s planning skipped - no process settings configured", window);
        } catch (RuntimeException e) {
            if (attempt < MAX_ATTEMPTS) {
                LOG.warnf(e, "%s planning failed (attempt %d of %d) - retrying in %s", window, attempt, MAX_ATTEMPTS,
                        RETRY_DELAY);
                later(RETRY_DELAY, () -> attemptDecision(window, attempt + 1));
            } else {
                LOG.errorf(e, "%s planning failed after %d attempts", window, MAX_ATTEMPTS);
                guardUseCase.reportFailure(window, String.valueOf(e.getMessage()));
            }
        }
    }

    private void verify(ChargeWindow window) {
        try {
            var result = guardUseCase.verify(window);
            LOG.infof("%s schedule read back: %s", window, result);
        } catch (RuntimeException e) {
            // The decision itself went through - a failed read-back isn't worth an alert on its own.
            LOG.warnf(e, "%s schedule read-back failed", window);
        }
    }

    /**
     * Runs a one-off task after a delay, off the scheduler's own thread. The request context is
     * activated by hand: Panache needs one, and only scheduler/HTTP threads get it for free.
     */
    private void later(Duration delay, Runnable task) {
        delayed.schedule(() -> {
            var requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.error("Delayed planning task failed", e);
            } finally {
                requestContext.terminate();
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        delayed.shutdownNow();
    }

    private void runSnapshot() {
        try {
            recordEnergySnapshotUseCase.recordSnapshot();
        } catch (ProcessSettingsNotConfiguredException e) {
            LOG.info("Energy snapshot skipped - no process settings configured");
        } catch (RuntimeException e) {
            // A missed snapshot only costs one measured period - never worth failing the job loudly.
            LOG.warnf(e, "Energy snapshot failed");
        }
    }
}
