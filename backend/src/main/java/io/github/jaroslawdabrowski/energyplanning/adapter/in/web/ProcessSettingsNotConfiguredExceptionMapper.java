package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettingsNotConfiguredException;

import java.util.Map;

/**
 * 409, not 500 - "nothing configured yet" is an expected, recoverable state (the user
 * just hasn't saved the settings panel yet), not a server error. {@code error} is a
 * translation key, matching the frontend's errorKey pattern.
 */
@Provider
public class ProcessSettingsNotConfiguredExceptionMapper
        implements ExceptionMapper<ProcessSettingsNotConfiguredException> {

    @Override
    public Response toResponse(ProcessSettingsNotConfiguredException exception) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "settings.notConfigured"))
                .build();
    }
}
