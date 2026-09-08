package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.github.jaroslawdabrowski.energyplanning.domain.DirectionPanelSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.InverterConnectionSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.PlanningSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ScheduleSettings;

import java.time.LocalTime;
import java.util.List;

/**
 * Wire shape for {@code GET}/{@code PUT /api/settings} - one record used for both
 * request and response bodies, mirroring {@link ProcessSettings} field-for-field except
 * times are plain "HH:mm" strings (simpler for an HTML {@code <input type="time">} than
 * Jackson's default LocalTime array/string shape).
 */
public record ProcessSettingsPayload(
        PlanningPayload planning,
        SchedulePayload schedule,
        ForecastPayload forecast,
        InverterConnectionPayload inverterConnection) {

    public record PlanningPayload(
            int minSocPercent,
            int fullSocPercent,
            double dailyConsumptionEstimateKwh,
            double afternoonConsumptionEstimateKwh,
            double forecastSafetyMarginRatio,
            List<String> cheapTariffWindows) {
    }

    public record SchedulePayload(String overnightTime, String afternoonTime) {
    }

    public record ForecastPayload(
            double latitude,
            double longitude,
            DirectionPayload north,
            DirectionPayload east,
            DirectionPayload south,
            DirectionPayload west) {

        public record DirectionPayload(int declinationDegrees, double kwp) {

            static DirectionPayload from(DirectionPanelSettings settings) {
                return new DirectionPayload(settings.declinationDegrees(), settings.kwp());
            }

            DirectionPanelSettings toDomain() {
                return new DirectionPanelSettings(declinationDegrees, kwp);
            }
        }
    }

    public record InverterConnectionPayload(String host, int port, long loggerSerial) {
    }

    public static ProcessSettingsPayload from(ProcessSettings settings) {
        var planning = settings.planning();
        var schedule = settings.schedule();
        var forecast = settings.forecast();
        var connection = settings.inverterConnection();
        return new ProcessSettingsPayload(
                new PlanningPayload(planning.minSocPercent(), planning.fullSocPercent(),
                        planning.dailyConsumptionEstimateKwh(), planning.afternoonConsumptionEstimateKwh(),
                        planning.forecastSafetyMarginRatio(), planning.cheapTariffWindows()),
                new SchedulePayload(schedule.overnightTime().toString(), schedule.afternoonTime().toString()),
                new ForecastPayload(forecast.latitude(), forecast.longitude(),
                        ForecastPayload.DirectionPayload.from(forecast.north()),
                        ForecastPayload.DirectionPayload.from(forecast.east()),
                        ForecastPayload.DirectionPayload.from(forecast.south()),
                        ForecastPayload.DirectionPayload.from(forecast.west())),
                new InverterConnectionPayload(connection.host(), connection.port(), connection.loggerSerial()));
    }

    public ProcessSettings toDomain() {
        return new ProcessSettings(
                new PlanningSettings(planning.minSocPercent(), planning.fullSocPercent(),
                        planning.dailyConsumptionEstimateKwh(), planning.afternoonConsumptionEstimateKwh(),
                        planning.forecastSafetyMarginRatio(), planning.cheapTariffWindows()),
                new ScheduleSettings(LocalTime.parse(schedule.overnightTime()), LocalTime.parse(schedule.afternoonTime())),
                new ForecastSettings(forecast.latitude(), forecast.longitude(),
                        forecast.north().toDomain(), forecast.east().toDomain(),
                        forecast.south().toDomain(), forecast.west().toDomain()),
                new InverterConnectionSettings(inverterConnection.host(), inverterConnection.port(),
                        inverterConnection.loggerSerial()));
    }
}
