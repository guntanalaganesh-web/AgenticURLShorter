package com.schwab.assessment.integration;

import com.schwab.assessment.api.ApiResponse;
import com.schwab.assessment.api.GateDecisionRequest;
import com.schwab.assessment.api.OrchestrationController;
import com.schwab.assessment.orchestration.GateKeeper;
import com.schwab.assessment.orchestration.OrchestrationEngine;
import com.schwab.assessment.orchestration.model.GateState;
import com.schwab.assessment.orchestration.model.PipelineStatus;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.scenario.GreenFieldScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the human-approval-gate rejection path against the real engine.
 * This needs {@code orchestration.gates.auto-approve=false}, whereas every
 * other integration test relies on the application default of {@code true}
 * so gates never block -- see {@code OrchestrationIntegrationTest}, which
 * runs greenfield/brownfield/ambiguous pipelines to completion without any
 * gate interaction at all. Because Spring's test context cache is keyed by
 * effective configuration (including {@link TestPropertySource}), this
 * class already gets its own {@code ApplicationContext} and therefore its
 * own fresh {@link GateKeeper} bean -- its {@code pendingGates}/{@code
 * gateStates} instance maps start empty regardless of what other test
 * classes have run. {@link DirtiesContext} is added anyway so this
 * non-default-config context is never left in Spring's context cache to be
 * reused by, or otherwise influence, another test class.
 */
@TestPropertySource(properties = "orchestration.gates.auto-approve=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GateRejectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GreenFieldScenario greenFieldScenario;

    @Autowired
    private OrchestrationEngine engine;

    @Autowired
    private GateKeeper gateKeeper;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void rejectingTheArchitectureGateHaltsThePipelineViaSafeStop() {
        // GreenFieldScenario.run() blocks the calling thread for the pipeline's
        // full duration, including the ARCHITECTURE gate wait -- run it on a
        // background thread (ForkJoinPool's common pool, via supplyAsync) so
        // this test thread is free to poll for the pending gate and reject it.
        CompletableFuture<PipelineStatus> pipelineRun = CompletableFuture.supplyAsync(greenFieldScenario::run);

        await().atMost(Duration.ofSeconds(10)).until(() -> engine.getCurrentRunId() != null);
        UUID runId = engine.getCurrentRunId();

        await().atMost(Duration.ofSeconds(10))
                .until(() -> gateKeeper.getGateState(runId, Stage.ARCHITECTURE) == GateState.PENDING);

        ResponseEntity<ApiResponse<OrchestrationController.GateResolutionResponse>> rejectResponse =
                restTemplate.exchange("/orchestration/gates/architecture/approve", HttpMethod.POST,
                        new HttpEntity<>(new GateDecisionRequest(false, "reviewer@test",
                                "architecture doesn't meet the latency budget")),
                        new ParameterizedTypeReference<ApiResponse<OrchestrationController.GateResolutionResponse>>() {
                        });
        assertThat(rejectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejectResponse.getBody()).isNotNull();
        assertThat(rejectResponse.getBody().data().approved()).isFalse();
        assertThat(rejectResponse.getBody().data().resolved()).isTrue();

        PipelineStatus status = pipelineRun.join();
        assertThat(status.halted()).isTrue();
        assertThat(status.overallStatus()).isEqualTo("HALTED");
        assertThat(status.haltReason()).contains("Gate rejected");

        await().atMost(Duration.ofSeconds(5))
                .until(() -> gateKeeper.getGateState(runId, Stage.ARCHITECTURE) == GateState.REJECTED);
    }
}
