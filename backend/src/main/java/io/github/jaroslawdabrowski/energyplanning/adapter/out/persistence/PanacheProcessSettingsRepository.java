package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;

import java.util.Optional;

/**
 * The only adapter that knows settings are stored as a JSON blob - that serialization
 * detail stays here, never in the domain (same pattern as reason params on
 * {@code ChargeDecisionEntity}).
 */
@ApplicationScoped
public class PanacheProcessSettingsRepository
        implements PanacheRepository<ProcessSettingsEntity>, ProcessSettingsPort {

    private final ObjectMapper objectMapper;

    public PanacheProcessSettingsRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProcessSettings> load() {
        return findByIdOptional(ProcessSettingsEntity.SINGLETON_ID).map(this::toDomain);
    }

    @Override
    @Transactional
    public void save(ProcessSettings settings) {
        var entity = findByIdOptional(ProcessSettingsEntity.SINGLETON_ID).orElseGet(ProcessSettingsEntity::new);
        entity.settingsJson = writeSettings(settings);
        persist(entity);
    }

    private ProcessSettings toDomain(ProcessSettingsEntity entity) {
        try {
            var json = (ObjectNode) objectMapper.readTree(entity.settingsJson);
            migrateConsumptionEstimates((ObjectNode) json.get("planning"));
            migrateOvernightRecheck((ObjectNode) json.get("schedule"));
            return objectMapper.treeToValue(json, ProcessSettings.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize process settings: " + entity.settingsJson, e);
        }
    }

    /**
     * Settings saved before the per-period consumption estimates existed only have a daily
     * total and a "rest of the day from the afternoon decision" figure. Derive the two
     * expensive periods (G12: 7 of 24 hours in the morning, 7 of the ~11 hours after 12:45 in
     * the evening) so the planner never silently sees 0 kWh - the next save from the
     * Settings page writes the new fields and drops the old ones.
     */
    static void migrateConsumptionEstimates(ObjectNode planning) {
        if (planning == null || planning.has("consumptionAfterOvernightKwh")) {
            return;
        }
        double daily = planning.path("dailyConsumptionEstimateKwh").asDouble(0);
        double afternoon = planning.path("afternoonConsumptionEstimateKwh").asDouble(0);
        planning.put("consumptionAfterOvernightKwh", roundToTenth(daily * 7 / 24));
        planning.put("consumptionAfterAfternoonKwh", roundToTenth(afternoon * 7 / 11));
        planning.remove("dailyConsumptionEstimateKwh");
        planning.remove("afternoonConsumptionEstimateKwh");
    }

    /**
     * Settings saved before the overnight recheck existed get it at 03:30 - it's a pure
     * improvement (a fresher forecast, charging closer to 06:00); clearing the field in
     * Settings turns it off, which is stored as an explicit null.
     */
    static void migrateOvernightRecheck(ObjectNode schedule) {
        if (schedule != null && !schedule.has("overnightRecheckTime")) {
            schedule.put("overnightRecheckTime", "03:30:00");
        }
    }

    private static double roundToTenth(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private String writeSettings(ProcessSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize process settings", e);
        }
    }
}
