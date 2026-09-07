package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Low-level REST client for the public api.forecast.solar API (free tier, no key required).
 * Visible only within this adapter package.
 */
@RegisterRestClient(configKey = "forecast-solar")
public interface ForecastSolarRestClient {

    @GET
    @Path("/estimate/{lat}/{lon}/{dec}/{az}/{kwp}")
    ForecastSolarEstimateResponse estimate(
            @PathParam("lat") double latitude,
            @PathParam("lon") double longitude,
            @PathParam("dec") int declination,
            @PathParam("az") int azimuth,
            @PathParam("kwp") double kwp);
}
