package com.schwab.assessment.integration;

import com.schwab.assessment.orchestration.ContextStore;
import com.schwab.assessment.orchestration.model.DecisionRecord;
import com.schwab.assessment.orchestration.model.PipelineStatus;
import com.schwab.assessment.orchestration.model.StageState;
import com.schwab.assessment.scenario.GreenFieldScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

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
    private ContextStore contextStore;

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
}
