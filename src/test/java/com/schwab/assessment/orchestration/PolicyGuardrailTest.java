package com.schwab.assessment.orchestration;

import com.schwab.assessment.orchestration.model.PipelineContext;
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

    private PipelineContext contextWithRequirement(String requirement) {
        Map<Stage, StageHandler> handlers = new EnumMap<>(Stage.class);
        return new PipelineContext(UUID.randomUUID(), "test", requirement, mock(ContextStore.class), handlers);
    }
}
