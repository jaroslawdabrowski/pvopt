package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetEnergyStatusUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetPlanningHistoryUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetTouScheduleUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanAfternoonTopUpUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanOvernightChargeUseCase;

import java.time.Instant;
import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class EnergyPlanningResource {

    private final PlanOvernightChargeUseCase planOvernightChargeUseCase;
    private final PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase;
    private final GetEnergyStatusUseCase getEnergyStatusUseCase;
    private final GetPlanningHistoryUseCase getPlanningHistoryUseCase;
    private final GetTouScheduleUseCase getTouScheduleUseCase;

    public EnergyPlanningResource(PlanOvernightChargeUseCase planOvernightChargeUseCase,
            PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase, GetEnergyStatusUseCase getEnergyStatusUseCase,
            GetPlanningHistoryUseCase getPlanningHistoryUseCase, GetTouScheduleUseCase getTouScheduleUseCase) {
        this.planOvernightChargeUseCase = planOvernightChargeUseCase;
        this.planAfternoonTopUpUseCase = planAfternoonTopUpUseCase;
        this.getEnergyStatusUseCase = getEnergyStatusUseCase;
        this.getPlanningHistoryUseCase = getPlanningHistoryUseCase;
        this.getTouScheduleUseCase = getTouScheduleUseCase;
    }

    @GET
    @Path("/status")
    public EnergyStatusResponse status() {
        return EnergyStatusResponse.from(getEnergyStatusUseCase.currentStatus());
    }

    @GET
    @Path("/history")
    public List<ChargeDecisionResponse> history(@QueryParam("from") String from, @QueryParam("to") String to) {
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minusSeconds(7 * 24 * 3600);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        return getPlanningHistoryUseCase.history(fromInstant, toInstant).stream()
                .map(ChargeDecisionResponse::from)
                .toList();
    }

    @GET
    @Path("/inverter/tou-schedule")
    public List<TouScheduleSlotResponse> touSchedule() {
        return getTouScheduleUseCase.currentTouSchedule().stream()
                .map(TouScheduleSlotResponse::from)
                .toList();
    }

    @POST
    @Path("/schedule/run-now/overnight")
    public ChargeDecisionResponse runOvernightNow() {
        return ChargeDecisionResponse.from(planOvernightChargeUseCase.planOvernightCharge());
    }

    @POST
    @Path("/schedule/run-now/afternoon")
    public ChargeDecisionResponse runAfternoonNow() {
        return ChargeDecisionResponse.from(planAfternoonTopUpUseCase.planAfternoonTopUp());
    }
}
