package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.openmeteo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A slice of the api.open-meteo.com response: hour stamps in the requested time zone and the
 * irradiance (W/m²) on the tilted plane, each value the mean over the hour <i>ending</i> at its
 * stamp. A value can be null where the model has no data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoResponse(Hourly hourly) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hourly(
            List<String> time,
            @JsonProperty("global_tilted_irradiance") List<Double> globalTiltedIrradiance) {
    }
}
