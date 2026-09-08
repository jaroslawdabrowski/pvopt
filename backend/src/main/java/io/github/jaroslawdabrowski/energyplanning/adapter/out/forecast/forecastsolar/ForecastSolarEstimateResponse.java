package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * A slice of the JSON response from api.forecast.solar/estimate - we care about the
 * daily production total (watt_hours_day: date -> Wh) and the cumulative-since-midnight
 * hourly series (watt_hours: timestamp -> Wh, used to compute "how much is left today").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForecastSolarEstimateResponse(Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("watt_hours_day") Map<String, Long> wattHoursDay,
            @JsonProperty("watt_hours") Map<String, Long> wattHours) {
    }
}
