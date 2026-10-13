package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessSettingsMigrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void derivesPerPeriodEstimatesFromTheOldDailyAndAfternoonFigures() throws Exception {
        var planning = (ObjectNode) mapper.readTree("""
                {"minSocPercent":20,"fullSocPercent":100,"dailyConsumptionEstimateKwh":16.0,
                 "afternoonConsumptionEstimateKwh":8.0,"forecastSafetyMarginRatio":1.2}""");

        PanacheProcessSettingsRepository.migrateConsumptionEstimates(planning);

        assertThat(planning.get("consumptionAfterOvernightKwh").asDouble()).isEqualTo(4.7);
        assertThat(planning.get("consumptionAfterAfternoonKwh").asDouble()).isEqualTo(5.1);
        assertThat(planning.has("dailyConsumptionEstimateKwh")).isFalse();
        assertThat(planning.has("afternoonConsumptionEstimateKwh")).isFalse();
    }

    @Test
    void leavesAlreadyMigratedSettingsAlone() throws Exception {
        var planning = (ObjectNode) mapper.readTree("""
                {"consumptionAfterOvernightKwh":3.0,"consumptionAfterAfternoonKwh":6.0}""");

        PanacheProcessSettingsRepository.migrateConsumptionEstimates(planning);

        assertThat(planning.get("consumptionAfterOvernightKwh").asDouble()).isEqualTo(3.0);
        assertThat(planning.get("consumptionAfterAfternoonKwh").asDouble()).isEqualTo(6.0);
    }
}
