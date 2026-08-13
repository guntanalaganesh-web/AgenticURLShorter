package com.schwab.assessment.integration;

import com.schwab.assessment.orchestration.ContextStore;
import com.schwab.assessment.orchestration.OrchestrationRunLogRepository;
import com.schwab.assessment.orchestration.model.AmbiguityRecord;
import com.schwab.assessment.orchestration.model.DecisionRecord;
import com.schwab.assessment.orchestration.model.OrchestrationRunLogEntity;
import com.schwab.assessment.orchestration.model.PipelineStatus;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageState;
import com.schwab.assessment.scenario.AmbiguousScenario;
import com.schwab.assessment.scenario.BrownFieldScenario;
import com.schwab.assessment.scenario.GreenFieldScenario;
import com.schwab.assessment.scenario.artifact.ReleaseChecklist;
import com.schwab.assessment.scenario.artifact.TaskPlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the greenfield scenario end-to-end against the real orchestration
 * stack (Postgres-backed {@link ContextStore}, auto-approved gates),
 * proving every stage reaches COMPLETED and that the decision lineage was
 * actually persisted, not just held in memory.
 */
class OrchestrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GreenFieldScenario greenFieldScenario;

    @Autowired
    private BrownFieldScenario brownFieldScenario;

    @Autowired
    private AmbiguousScenario ambiguousScenario;

    @Autowired
    private ContextStore contextStore;

    @Autowired
    private OrchestrationRunLogRepository runLogRepository;

    @Test
    void greenfieldPipelineCompletesAllStagesAndPersistsDecisions() {
        PipelineStatus status = greenFieldScenario.run();

        assertThat(status.halted()).isFalse();
        assertThat(status.overallStatus()).isEqualTo("COMPLETED");
        assertThat(status.stageStates().values()).allMatch(state -> state == StageState.COMPLETED);

        List<DecisionRecord> decisions = contextStore.getDecisionLog(status.runId());
        assertThat(decisions).isNotEmpty();
        assertThat(decisions).anyMatch(d -> d.decision().toLowerCase().contains("cache"));
    }

    @Test
    void brownfieldPipelineRetriesRollsBackAndRecoversFromMigrationCollision() {
        PipelineStatus status = brownFieldScenario.run();

        assertThat(status.halted()).isFalse();
        assertThat(status.overallStatus()).isEqualTo("COMPLETED");
        assertThat(status.stageStates().values()).allMatch(state -> state == StageState.COMPLETED);

        // IMPLEMENTATION throws a MigrationVersionCollisionException on attempt 1 and
        // succeeds on attempt 2 (see BrownFieldScenario.implementation()); StageTiming's
        // attemptCount is the per-stage retry count StageExecutor rolled back and retried through.
        assertThat(status.stageTimings().get(Stage.IMPLEMENTATION).attemptCount()).isEqualTo(2);

        List<DecisionRecord> decisions = contextStore.getDecisionLog(status.runId());
        assertThat(decisions).anyMatch(d -> d.decision().toLowerCase().contains("renumber")
                && d.rationale().toLowerCase().contains("rolled back"));

        Optional<OrchestrationRunLogEntity> runLogEntry = runLogRepository.findByRunId(status.runId());
        assertThat(runLogEntry).isPresent();
        assertThat(runLogEntry.get().getSuccess()).isTrue();
    }

    @Test
    void ambiguousScenarioSurfacesThreeAmbiguitiesAndDynamicallyRePlansAfterArchitectureRevision() {
        PipelineStatus status = ambiguousScenario.run();

        assertThat(status.halted()).isFalse();
        assertThat(status.overallStatus()).isEqualTo("COMPLETED");

        List<AmbiguityRecord> ambiguities = contextStore.getAmbiguityLog(status.runId());
        assertThat(ambiguities).hasSize(3);

        // OrchestrationEngine.reviseStageOutput() records this decision when the
        // post-TASK_PLANNING hook revises ARCHITECTURE's rate limit mid-run.
        List<DecisionRecord> decisions = contextStore.getDecisionLog(status.runId());
        assertThat(decisions).anyMatch(d -> d.decision().contains("Revised ARCHITECTURE"));

        // TASK_PLANNING is downstream of ARCHITECTURE, so the revision invalidates and
        // re-executes it -- its re-generated output should reflect the revised limit.
        TaskPlan revisedPlan = contextStore.getStageOutput(status.runId(), Stage.TASK_PLANNING, TaskPlan.class);
        assertThat(revisedPlan.tasks()).anyMatch(t -> t.description().contains("60 req/min (revised)"));

        ReleaseChecklist checklist =
                contextStore.getStageOutput(status.runId(), Stage.RELEASE_READINESS, ReleaseChecklist.class);
        assertThat(checklist.items()).anyMatch(item -> item.name().contains("Final rate limit")
                && item.notes().contains("60 req/min (revised)"));
    }
}
