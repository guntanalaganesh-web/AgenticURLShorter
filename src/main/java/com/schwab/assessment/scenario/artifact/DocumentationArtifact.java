package com.schwab.assessment.scenario.artifact;

import com.schwab.assessment.orchestration.model.OpenApiPublished;

/**
 * DOCUMENTATION stage output: whether and where an OpenAPI spec was
 * published, and how many ADRs back this run's decisions. Implements
 * {@link OpenApiPublished} so {@link com.schwab.assessment.orchestration.PolicyGuardrail}
 * can enforce the RELEASE_READINESS OpenAPI-spec requirement.
 */
public record DocumentationArtifact(boolean hasOpenApiSpec, String openApiLocation, int adrCount)
        implements OpenApiPublished {
}
