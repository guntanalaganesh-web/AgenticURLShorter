package com.schwab.assessment.orchestration.model;

/**
 * Implemented by a DOCUMENTATION stage artifact so
 * {@link com.schwab.assessment.orchestration.PolicyGuardrail} can verify an
 * OpenAPI spec exists before RELEASE_READINESS is allowed to proceed.
 */
public interface OpenApiPublished {

    boolean hasOpenApiSpec();

    String openApiLocation();
}
