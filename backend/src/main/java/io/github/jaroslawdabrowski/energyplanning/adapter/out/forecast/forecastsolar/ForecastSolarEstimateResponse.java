package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * A slice of the JSON response from api.forecast.solar/estimate - we only care
 * about the daily production total (watt_hours_day: date -> Wh).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForecastSolarEstimateResponse(Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(@JsonProperty("watt_hours_day") Map<String, Long> wattHoursDay) {
    }
}
