package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.DecisionReason;
import io.github.jaroslawdabrowski.energyplanning.port.out.PlanningHistoryPort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The only adapter that knows about Panache/JPA - maps the entity to/from the
 * domain-level ChargeDecision. Reason parameters are stored as a JSON blob;
 * that serialization detail stays inside this adapter, never in the domain.
 */
@ApplicationScoped
public class PanacheChargeDecisionRepository implements PanacheRepository<ChargeDecisionEntity>, PlanningHistoryPort {

    private static final TypeReference<Map<String, Double>> REASON_PARAMS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public PanacheChargeDecisionRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void record(ChargeDecision decision) {
        persist(toEntity(decision));
    }

    @Override
    public List<ChargeDecision> findBetween(Instant from, Instant to) {
        return find("decidedAt >= ?1 and decidedAt <= ?2 order by decidedAt", from, to)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<ChargeDecision> findLatest() {
        return find("order by decidedAt desc").firstResultOptional().map(this::toDomain);
    }

    private ChargeDecisionEntity toEntity(ChargeDecision decision) {
        var entity = new ChargeDecisionEntity();
        entity.decidedAt = decision.decidedAt();
        entity.window = decision.window().name();
        entity.chargeFromGrid = decision.chargeFromGrid();
        entity.targetSocPercent = decision.targetSocPercent();
        entity.reason = decision.reason().name();
        entity.reasonParamsJson = writeReasonParams(decision.reasonParams());
        return entity;
    }

    private ChargeDecision toDomain(ChargeDecisionEntity entity) {
        return new ChargeDecision(entity.decidedAt, ChargeWindow.valueOf(entity.window), entity.chargeFromGrid,
                entity.targetSocPercent, DecisionReason.valueOf(entity.reason),
                readReasonParams(entity.reasonParamsJson));
    }

    private String writeReasonParams(Map<String, Double> reasonParams) {
        try {
            return objectMapper.writeValueAsString(reasonParams);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize reason params", e);
        }
    }

    private Map<String, Double> readReasonParams(String json) {
        try {
            return objectMapper.readValue(json, REASON_PARAMS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize reason params: " + json, e);
        }
    }
}
