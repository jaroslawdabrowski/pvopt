package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.github.jaroslawdabrowski.energyplanning.port.in.GetLivePowerUseCase;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.List;

/** The power flows right now - polled by the dashboard's live panel. */
@Path("/api/energy/live")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class LivePowerResource {

    private final GetLivePowerUseCase useCase;

    public LivePowerResource(GetLivePowerUseCase useCase) {
        this.useCase = useCase;
    }

    @GET
    public LivePowerResponse live() {
        var reading = useCase.livePower();
        var power = reading.power();
        return new LivePowerResponse(reading.readAt(), power.pvInputWatts(), power.pvWatts(), power.loadWatts(),
                power.batteryWatts(), power.gridWatts(), power.batterySocPercent());
    }

    /** Watts; {@code batteryWatts} > 0 = discharging, {@code gridWatts} > 0 = buying. */
    public record LivePowerResponse(Instant readAt, List<Integer> pvInputWatts, int pvWatts, int loadWatts,
            int batteryWatts, int gridWatts, int batterySocPercent) {
    }
}
