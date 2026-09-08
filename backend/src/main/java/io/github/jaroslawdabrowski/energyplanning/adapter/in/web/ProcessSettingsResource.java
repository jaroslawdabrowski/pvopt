package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetProcessSettingsUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.UpdateProcessSettingsUseCase;

@Path("/api/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ProcessSettingsResource {

    private final GetProcessSettingsUseCase getProcessSettingsUseCase;
    private final UpdateProcessSettingsUseCase updateProcessSettingsUseCase;

    public ProcessSettingsResource(GetProcessSettingsUseCase getProcessSettingsUseCase,
            UpdateProcessSettingsUseCase updateProcessSettingsUseCase) {
        this.getProcessSettingsUseCase = getProcessSettingsUseCase;
        this.updateProcessSettingsUseCase = updateProcessSettingsUseCase;
    }

    @GET
    public ProcessSettingsStatusResponse get() {
        return getProcessSettingsUseCase.currentSettings()
                .map(settings -> new ProcessSettingsStatusResponse(true, ProcessSettingsPayload.from(settings)))
                .orElseGet(() -> new ProcessSettingsStatusResponse(false, null));
    }

    @PUT
    public ProcessSettingsPayload set(ProcessSettingsPayload payload) {
        var saved = updateProcessSettingsUseCase.updateSettings(payload.toDomain());
        return ProcessSettingsPayload.from(saved);
    }
}
