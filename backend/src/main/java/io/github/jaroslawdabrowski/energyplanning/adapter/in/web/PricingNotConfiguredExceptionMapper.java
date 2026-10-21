package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import io.github.jaroslawdabrowski.energyplanning.domain.PricingNotConfiguredException;

import java.util.Map;

/**
 * 409, not 500 - "nothing configured yet" is an expected, recoverable state (the user
 * just hasn't saved the settings panel yet), not a server error. {@code error} is a
 * translation key, matching the frontend's errorKey pattern.
 */
@Provider
public class PricingNotConfiguredExceptionMapper
        implements ExceptionMapper<PricingNotConfiguredException> {

    @Override
    public Response toResponse(PricingNotConfiguredException exception) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "pricing.notConfigured"))
                .build();
    }
}
