package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.openmeteo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Open-Meteo's archive of past forecasts (historical-forecast-api.open-meteo.com) - the same
 * models as the live forecast, so it says what the forecast had for hours that are now measured.
 * Visible only within this adapter package.
 */
@RegisterRestClient(configKey = "open-meteo-historical")
public interface OpenMeteoHistoricalRestClient {

    @GET
    @Path("/v1/forecast")
    OpenMeteoResponse tiltedIrradiance(
            @QueryParam("latitude") double latitude,
            @QueryParam("longitude") double longitude,
            @QueryParam("tilt") int tilt,
            @QueryParam("azimuth") int azimuth,
            @QueryParam("hourly") String hourly,
            @QueryParam("start_date") String startDate,
            @QueryParam("end_date") String endDate,
            @QueryParam("timezone") String timezone);
}
