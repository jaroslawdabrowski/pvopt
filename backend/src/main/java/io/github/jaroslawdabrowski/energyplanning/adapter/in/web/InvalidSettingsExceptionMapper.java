package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * The settings domain records validate themselves in their compact constructors (SOC
 * range, positive kWp, etc.) and throw IllegalArgumentException - this maps that to a
 * 400 with the message, instead of it falling through to a generic 500.
 */
@Provider
public class InvalidSettingsExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", exception.getMessage()))
                .build();
    }
}
