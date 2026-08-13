package com.schwab.assessment.orchestration;

import com.schwab.assessment.orchestration.model.OrchestrationRunLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data access to the {@code orchestration_run_log} table. Internal
 * to {@link ObservabilityCollector}.
 */
public interface OrchestrationRunLogRepository extends JpaRepository<OrchestrationRunLogEntity, UUID> {

    Optional<OrchestrationRunLogEntity> findByRunId(UUID runId);

    List<OrchestrationRunLogEntity> findAllByOrderByStartedAtDesc();
}
