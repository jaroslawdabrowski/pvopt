package io.github.jaroslawdabrowski.energyplanning.adapter.in.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanEnergyUseCase;

@ApplicationScoped
public class EnergyPlanningSchedule {

    private static final Logger LOG = Logger.getLogger(EnergyPlanningSchedule.class);

    private final PlanEnergyUseCase planEnergyUseCase;

    public EnergyPlanningSchedule(PlanEnergyUseCase planEnergyUseCase) {
        this.planEnergyUseCase = planEnergyUseCase;
    }

    @Scheduled(cron = "0 0 * * * ?")
    void planEveryHour() {
        var decision = planEnergyUseCase.planAndApply();
        LOG.infof("Hourly energy planning decision: chargeFromGrid=%s, targetSoc=%d%%, reason=%s, params=%s",
                decision.chargeFromGrid(), decision.targetSocPercent(), decision.reason(), decision.reasonParams());
    }
}
