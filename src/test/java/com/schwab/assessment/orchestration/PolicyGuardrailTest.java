package com.schwab.assessment.orchestration;

import com.schwab.assessment.orchestration.model.CoverageReporting;
import com.schwab.assessment.orchestration.model.MigrationAudit;
import com.schwab.assessment.orchestration.model.OpenApiPublished;
import com.schwab.assessment.orchestration.model.PipelineContext;
import com.schwab.assessment.orchestration.model.PolicyScannable;
import com.schwab.assessment.orchestration.model.PolicyViolationException;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageHandler;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PolicyGuardrailTest {

    private final PolicyGuardrail guardrail = new PolicyGuardrail();

    @Test
    void cleanRequirementPassesValidation() {
        PipelineContext context = contextWithRequirement(
                "Build a URL shortener that redirects to https://example.com/target-page");

        assertDoesNotThrow(() -> guardrail.validate(Stage.REQUIREMENTS, context));
    }

    @Test
    void ssrfRiskUrlIsRejected() {
        PipelineContext context = contextWithRequirement(
                "Fetch instance metadata from http://169.254.169.254/latest/meta-data/");

        PolicyViolationException exception = assertThrows(PolicyViolationException.class,
                () -> guardrail.validate(Stage.REQUIREMENTS, context));
        assertEquals("SSRF-RISK-HOST", exception.getRule());
    }

    @Test
    void privateRangeHostIsRejectedAsSsrfRisk() {
        PipelineContext context = contextWithRequirement("Integrate with the internal service at http://10.0.5.12/api");

        PolicyViolationException exception = assertThrows(PolicyViolationException.class,
                () -> guardrail.validate(Stage.REQUIREMENTS, context));
        assertEquals("SSRF-RISK-HOST", exception.getRule());
    }

    @Test
    void emailAddressInRequirementIsFlaggedAsPii() {
        PipelineContext context = contextWithRequirement("Notify the requester at john.doe@example.com when done");

        PolicyViolationException exception = assertThrows(PolicyViolationException.class,
                () -> guardrail.validate(Stage.REQUIREMENTS, context));
        assertEquals("PII-IN-REQUIREMENTS", exception.getRule());
    }

    @Test
    void ssnShapedSequenceInRequirementIsFlaggedAsPii() {
        PipelineContext context = contextWithRequirement("Look up the customer record for 123-45-6789");

        PolicyViolationException exception = assertThrows(PolicyViolationException.class,
                () -> guardrail.validate(Stage.REQUIREMENTS, context));
        assertEquals("PII-IN-REQUIREMENTS", exception.getRule());
    }

    // --- IMPLEMENTATION stage: PolicyGuardrail scans the TASK_PLANNING
    // artifact (not an "IMPLEMENTATION artifact" -- there isn't one yet at
    // the point this guardrail runs, since it gates whether IMPLEMENTATION
    // is even allowed to start). See PolicyGuardrail.validateImplementation(). ---

    @Test
    void hardcodedPasswordInTaskPlan_rejectedAtImplementationStage() {
        PipelineContext context = contextWithRequirement("Build a URL shortener");
        context.setArtifact(Stage.TASK_PLANNING,
                new FakeScannableArtifact("Task: set password=hunter2 in application.yml for now"));

        PolicyViolationException exception = assertThrows(PolicyViolationException.class,
                () -> guardrail.validate(Stage.IMPLEMENTATION, context));
        assertEquals("NO-HARDCODED-SECRETS", exception.getRule());
    }

    @Test
    void sqlInjectionPatternInTaskPlan_rejectedAtImplementationStage() {
        PipelineContext context = contextWithRequirement("Build a URL shortener");
        context.setArtifact(Stage.TASK_PLANNING,
                new FakeScannableArtifact("query = \"SELECT * FROM users WHERE id='\" + userId"));

        PolicyViolationException exception = assertThrows(PolicyViolationException.class,
                () -> guardrail.validate(Stage.IMPLEMENTATION, context));
        assertEquals("OWASP-TOP10-PATTERN", exception.getRule());
    }

    @Test
    void cleanTaskPlan_passesImplementationStage() {
        PipelineContext context = contextWithRequirement("Build a URL shortener");
        context.setArtifact(Stage.TASK_PLANNING,
                new FakeScannableArtifact("Implement UrlService with Redis cache-aside reads"));

        assertDoesNotThrow(() -> guardrail.validate(Stage.IMPLEMENTATION, context));
    }

    // --- RELEASE_READINESS stage: coverage, then OpenAPI spec, then migration
    // review, checked in that order (see PolicyGuardrail.validateReleaseReadiness()) --
    // so a "passes" test must satisfy all three, not just the one under test. ---

    @Test
    void coverageBelowThreshold_rejectedAtReleaseReadinessStage() {
        PipelineContext context = contextWithRequirement("Build a URL shortener");
        context.setArtifact(Stage.TESTING, new FakeCoverageArtifact(74.0));

        PolicyViolationException exception = assertThrows(PolicyViolationException.class,
                () -> guardrail.validate(Stage.RELEASE_READINESS, context));
        assertEquals("COVERAGE-THRESHOLD", exception.getRule());
    }

    @Test
    void coverageAboveThreshold_passesReleaseReadinessStage() {
        PipelineContext context = contextWithRequirement("Build a URL shortener");
        context.setArtifact(Stage.TESTING, new FakeCoverageArtifact(82.0));
        context.setArtifact(Stage.DOCUMENTATION, new FakeDocArtifact(true, "/v3/api-docs"));
        context.setArtifact(Stage.IMPLEMENTATION, new FakeMigrationArtifact(true, 2));

        assertDoesNotThrow(() -> guardrail.validate(Stage.RELEASE_READINESS, context));
    }

    @Test
    void openApiSpecMissing_rejectedAtReleaseReadinessStage() {
        PipelineContext context = contextWithRequirement("Build a URL shortener");
        context.setArtifact(Stage.TESTING, new FakeCoverageArtifact(82.0));
        context.setArtifact(Stage.DOCUMENTATION, new FakeDocArtifact(false, null));

        PolicyViolationException exception = assertThrows(PolicyViolationException.class,
                () -> guardrail.validate(Stage.RELEASE_READINESS, context));
        assertEquals("OPENAPI-SPEC-REQUIRED", exception.getRule());
    }

    private PipelineContext contextWithRequirement(String requirement) {
        Map<Stage, StageHandler> handlers = new EnumMap<>(Stage.class);
        return new PipelineContext(UUID.randomUUID(), "test", requirement, mock(ContextStore.class), handlers);
    }

    // Minimal fakes for the marker interfaces PolicyGuardrail inspects artifacts
    // through -- same "record component name matches interface accessor" pattern
    // the real scenario artifacts (TaskPlan, TestReport, ...) already use.
    private record FakeScannableArtifact(String policyScanText) implements PolicyScannable {
    }

    private record FakeCoverageArtifact(double coveragePercentage) implements CoverageReporting {
    }

    private record FakeDocArtifact(boolean hasOpenApiSpec, String openApiLocation) implements OpenApiPublished {
    }

    private record FakeMigrationArtifact(boolean migrationScriptsReviewed, int migrationCount) implements MigrationAudit {
    }
}
