package io.github.jaroslawdabrowski.energyplanning.adapter.in.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanAfternoonTopUpUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanOvernightChargeUseCase;

/**
 * G12 has exactly two cheap windows, so exactly two decisions are needed per day -
 * not hourly. Each job runs shortly before its window starts, leaving enough time
 * for the Modbus write to land before the inverter's own TOU slot takes over.
 */
@ApplicationScoped
public class EnergyPlanningSchedule {

    private static final Logger LOG = Logger.getLogger(EnergyPlanningSchedule.class);

    private final PlanOvernightChargeUseCase planOvernightChargeUseCase;
    private final PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase;

    public EnergyPlanningSchedule(PlanOvernightChargeUseCase planOvernightChargeUseCase,
            PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase) {
        this.planOvernightChargeUseCase = planOvernightChargeUseCase;
        this.planAfternoonTopUpUseCase = planAfternoonTopUpUseCase;
    }

    /** Shortly before the 22:00 overnight window starts. */
    @Scheduled(cron = "0 45 21 * * ?")
    void planOvernight() {
        var decision = planOvernightChargeUseCase.planOvernightCharge();
        LOG.infof("Overnight planning decision: chargeFromGrid=%s, targetSoc=%d%%, reason=%s, params=%s",
                decision.chargeFromGrid(), decision.targetSocPercent(), decision.reason(), decision.reasonParams());
    }

    /** Shortly before the 13:00 afternoon window starts. */
    @Scheduled(cron = "0 45 12 * * ?")
    void planAfternoon() {
        var decision = planAfternoonTopUpUseCase.planAfternoonTopUp();
        LOG.infof("Afternoon planning decision: chargeFromGrid=%s, targetSoc=%d%%, reason=%s, params=%s",
                decision.chargeFromGrid(), decision.targetSocPercent(), decision.reason(), decision.reasonParams());
    }
}
