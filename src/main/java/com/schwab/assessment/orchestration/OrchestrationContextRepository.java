package com.schwab.assessment.orchestration;

import com.schwab.assessment.orchestration.model.OrchestrationContextEntity;
import com.schwab.assessment.orchestration.model.Stage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data access to the {@code orchestration_context} table. Internal
 * to {@link ContextStore}; other components should go through ContextStore
 * rather than this repository directly.
 */
public interface OrchestrationContextRepository extends JpaRepository<OrchestrationContextEntity, UUID> {

    List<OrchestrationContextEntity> findAllByOrderByCreatedAtDesc();

    List<OrchestrationContextEntity> findByRunIdOrderByCreatedAtAsc(UUID runId);

    List<OrchestrationContextEntity> findByKeyOrderByCreatedAtDesc(String key);

    List<OrchestrationContextEntity> findByRunIdAndKeyOrderByCreatedAtAsc(UUID runId, String key);

    Optional<OrchestrationContextEntity> findFirstByRunIdAndStageAndKeyOrderByCreatedAtDesc(
            UUID runId, Stage stage, String key);
}
