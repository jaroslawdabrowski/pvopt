package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            return objectMapper.readValue(entity.settingsJson, ProcessSettings.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize process settings: " + entity.settingsJson, e);
        }
    }

    private String writeSettings(ProcessSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize process settings", e);
        }
    }
}
