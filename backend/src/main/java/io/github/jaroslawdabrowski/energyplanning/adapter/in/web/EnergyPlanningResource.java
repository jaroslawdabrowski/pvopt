package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.NotificationSettings;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetEnergyStatusUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetLearnedConsumptionUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetPlanningHistoryUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetTouScheduleUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GuardChargeScheduleUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanAfternoonTopUpUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanOvernightChargeUseCase;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class EnergyPlanningResource {

    private final PlanOvernightChargeUseCase planOvernightChargeUseCase;
    private final PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase;
    private final GetEnergyStatusUseCase getEnergyStatusUseCase;
    private final GetPlanningHistoryUseCase getPlanningHistoryUseCase;
    private final GetTouScheduleUseCase getTouScheduleUseCase;
    private final GetLearnedConsumptionUseCase getLearnedConsumptionUseCase;
    private final GuardChargeScheduleUseCase guardUseCase;

    public EnergyPlanningResource(PlanOvernightChargeUseCase planOvernightChargeUseCase,
            PlanAfternoonTopUpUseCase planAfternoonTopUpUseCase, GetEnergyStatusUseCase getEnergyStatusUseCase,
            GetPlanningHistoryUseCase getPlanningHistoryUseCase, GetTouScheduleUseCase getTouScheduleUseCase,
            GetLearnedConsumptionUseCase getLearnedConsumptionUseCase, GuardChargeScheduleUseCase guardUseCase) {
        this.planOvernightChargeUseCase = planOvernightChargeUseCase;
        this.planAfternoonTopUpUseCase = planAfternoonTopUpUseCase;
        this.getEnergyStatusUseCase = getEnergyStatusUseCase;
        this.getPlanningHistoryUseCase = getPlanningHistoryUseCase;
        this.getTouScheduleUseCase = getTouScheduleUseCase;
        this.getLearnedConsumptionUseCase = getLearnedConsumptionUseCase;
        this.guardUseCase = guardUseCase;
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

    /** Measured consumption per window, and how far learning has got. */
    @GET
    @Path("/energy/learned-consumption")
    public Map<String, LearnedConsumptionResponse> learnedConsumption() {
        var result = new LinkedHashMap<String, LearnedConsumptionResponse>();
        for (ChargeWindow window : ChargeWindow.values()) {
            var learning = getLearnedConsumptionUseCase.learnedConsumption(window);
            result.put(window.name(), new LearnedConsumptionResponse(
                    learning.learned().map(l -> l.kwh()).orElse(null),
                    learning.samples(),
                    learning.countingFrom().orElse(null)));
        }
        return result;
    }

    /** {@code kwh} is null until there are enough samples; {@code countingFrom} is set after a recent estimate change. */
    public record LearnedConsumptionResponse(Double kwh, int samples, LocalDateTime countingFrom) {
    }

    /** Sends a test notification to the URL in the body - the Settings form can test it before saving. */
    @POST
    @Path("/notifications/test")
    @Consumes(MediaType.APPLICATION_JSON)
    public TestNotificationResponse testNotification(ProcessSettingsPayload.NotificationsPayload payload) {
        var settings = new NotificationSettings(payload == null ? "" : payload.ntfyUrl());
        return new TestNotificationResponse(guardUseCase.sendTestNotification(settings));
    }

    public record TestNotificationResponse(boolean delivered) {
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
