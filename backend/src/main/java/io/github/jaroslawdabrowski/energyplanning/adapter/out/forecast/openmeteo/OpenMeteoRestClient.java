package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.openmeteo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Low-level REST client for the public api.open-meteo.com forecast API (free, no key).
 * Visible only within this adapter package.
 */
@RegisterRestClient(configKey = "open-meteo")
public interface OpenMeteoRestClient {

    /** Hourly irradiance on a plane with the given tilt and azimuth (0 = south, -90 = east, 90 = west). */
    @GET
    @Path("/v1/forecast")
    OpenMeteoResponse tiltedIrradiance(
            @QueryParam("latitude") double latitude,
            @QueryParam("longitude") double longitude,
            @QueryParam("tilt") int tilt,
            @QueryParam("azimuth") int azimuth,
            @QueryParam("hourly") String hourly,
            @QueryParam("forecast_days") int forecastDays,
            @QueryParam("timezone") String timezone);
}
