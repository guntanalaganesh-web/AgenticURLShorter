package com.schwab.assessment.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.assessment.orchestration.model.AmbiguityRecord;
import com.schwab.assessment.orchestration.model.DecisionRecord;
import com.schwab.assessment.orchestration.model.OrchestrationContextEntity;
import com.schwab.assessment.orchestration.model.Stage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Preserves cross-stage context and decision lineage for the orchestration
 * engine, backed by the {@code orchestration_context} PostgreSQL table.
 * Three kinds of records are stored, distinguished by a logical key:
 * architectural/engineering decisions with rationale, ambiguity
 * resolutions with confidence and impact, and the raw output artifact
 * produced by each stage.
 */
@Component
public class ContextStore {

    private static final String KEY_DECISION = "decision";
    private static final String KEY_AMBIGUITY = "ambiguity";
    private static final String KEY_STAGE_OUTPUT = "stage_output";

    private final OrchestrationContextRepository repository;
    private final ObjectMapper objectMapper;

    public ContextStore(OrchestrationContextRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DecisionRecord saveDecision(DecisionRecord record) {
        persist(record.runId(), record.stage(), KEY_DECISION, record);
        return record;
    }

    /**
     * The full decision log across every run, most recent first.
     */
    @Transactional(readOnly = true)
    public List<DecisionRecord> getDecisionLog() {
        return repository.findByKeyOrderByCreatedAtDesc(KEY_DECISION).stream()
                .map(entity -> readEnvelope(entity, DecisionRecord.class))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DecisionRecord> getDecisionLog(UUID runId) {
        return repository.findByRunIdAndKeyOrderByCreatedAtAsc(runId, KEY_DECISION).stream()
                .map(entity -> readEnvelope(entity, DecisionRecord.class))
                .toList();
    }

    @Transactional
    public AmbiguityRecord saveAmbiguity(AmbiguityRecord record) {
        persist(record.runId(), record.stage(), KEY_AMBIGUITY, record);
        return record;
    }

    /**
     * The full ambiguity log across every run, most recent first.
     */
    @Transactional(readOnly = true)
    public List<AmbiguityRecord> getAmbiguityLog() {
        return repository.findByKeyOrderByCreatedAtDesc(KEY_AMBIGUITY).stream()
                .map(entity -> readEnvelope(entity, AmbiguityRecord.class))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AmbiguityRecord> getAmbiguityLog(UUID runId) {
        return repository.findByRunIdAndKeyOrderByCreatedAtAsc(runId, KEY_AMBIGUITY).stream()
                .map(entity -> readEnvelope(entity, AmbiguityRecord.class))
                .toList();
    }

    @Transactional
    public void saveStageOutput(UUID runId, Stage stage, Object artifact) {
        persist(runId, stage, KEY_STAGE_OUTPUT, artifact);
    }

    @Transactional(readOnly = true)
    public Object getStageOutput(UUID runId, Stage stage) {
        return repository.findFirstByRunIdAndStageAndKeyOrderByCreatedAtDesc(runId, stage, KEY_STAGE_OUTPUT)
                .map(this::readEnvelopeGeneric)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public <T> T getStageOutput(UUID runId, Stage stage, Class<T> type) {
        Optional<OrchestrationContextEntity> entity =
                repository.findFirstByRunIdAndStageAndKeyOrderByCreatedAtDesc(runId, stage, KEY_STAGE_OUTPUT);
        return entity.map(e -> readEnvelope(e, type)).orElse(null);
    }

    private void persist(UUID runId, Stage stage, String key, Object payload) {
        try {
            Envelope envelope = new Envelope(payload.getClass().getName(), objectMapper.valueToTree(payload));
            String json = objectMapper.writeValueAsString(envelope);
            repository.save(new OrchestrationContextEntity(runId, stage, key, json));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist orchestration context for run " + runId, e);
        }
    }

    private <T> T readEnvelope(OrchestrationContextEntity entity, Class<T> expectedType) {
        try {
            Envelope envelope = objectMapper.readValue(entity.getValue(), Envelope.class);
            return objectMapper.treeToValue(envelope.payload(), expectedType);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize orchestration context row " + entity.getId(), e);
        }
    }

    private Object readEnvelopeGeneric(OrchestrationContextEntity entity) {
        try {
            Envelope envelope = objectMapper.readValue(entity.getValue(), Envelope.class);
            Class<?> clazz = Class.forName(envelope.className());
            return objectMapper.treeToValue(envelope.payload(), clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize orchestration context row " + entity.getId(), e);
        }
    }

    private record Envelope(String className, JsonNode payload) {
    }
}
