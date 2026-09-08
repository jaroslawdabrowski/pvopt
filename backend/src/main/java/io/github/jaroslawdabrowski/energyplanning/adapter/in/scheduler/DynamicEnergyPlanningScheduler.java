package io.github.jaroslawdabrowski.energyplanning.adapter.in.scheduler;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettingsNotConfiguredException;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetProcessSettingsUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanAfternoonTopUpUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanOvernightChargeUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.SchedulingPort;

import java.time.LocalTime;
import java.util.Optional;

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
    private static final String AFTERNOON_JOB = "afternoon-topup";

    private final Scheduler scheduler;
    private final PlanOvernightChargeUseCase planOvernightChargeUseCase;
    private final PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase;
    private final GetProcessSettingsUseCase getProcessSettingsUseCase;

    public DynamicEnergyPlanningScheduler(Scheduler scheduler, PlanOvernightChargeUseCase planOvernightChargeUseCase,
            PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase, GetProcessSettingsUseCase getProcessSettingsUseCase) {
        this.scheduler = scheduler;
        this.planOvernightChargeUseCase = planOvernightChargeUseCase;
        this.planAfternoonTopUpUseCase = planAfternoonTopUpUseCase;
        this.getProcessSettingsUseCase = getProcessSettingsUseCase;
    }

    void onStart(@Observes StartupEvent event) {
        reschedule(getProcessSettingsUseCase.currentSettings());
    }

    @Override
    public void reschedule(Optional<ProcessSettings> settings) {
        unscheduleIfPresent(OVERNIGHT_JOB);
        unscheduleIfPresent(AFTERNOON_JOB);

        settings.ifPresentOrElse(s -> {
            scheduleDaily(OVERNIGHT_JOB, s.schedule().overnightTime(), this::runOvernight);
            scheduleDaily(AFTERNOON_JOB, s.schedule().afternoonTime(), this::runAfternoon);
            LOG.infof("Scheduled jobs registered: overnight=%s, afternoon=%s",
                    s.schedule().overnightTime(), s.schedule().afternoonTime());
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
        try {
            var decision = planOvernightChargeUseCase.planOvernightCharge();
            LOG.infof("Overnight planning decision: chargeFromGrid=%s, targetSoc=%d%%, reason=%s, params=%s",
                    decision.chargeFromGrid(), decision.targetSocPercent(), decision.reason(), decision.reasonParams());
        } catch (ProcessSettingsNotConfiguredException e) {
            LOG.info("Overnight planning skipped - no process settings configured");
        }
    }

    private void runAfternoon() {
        try {
            var decision = planAfternoonTopUpUseCase.planAfternoonTopUp();
            LOG.infof("Afternoon planning decision: chargeFromGrid=%s, targetSoc=%d%%, reason=%s, params=%s",
                    decision.chargeFromGrid(), decision.targetSocPercent(), decision.reason(), decision.reasonParams());
        } catch (ProcessSettingsNotConfiguredException e) {
            LOG.info("Afternoon planning skipped - no process settings configured");
        }
    }
}
