package com.schwab.assessment.orchestration.model;

/**
 * Implemented by a TESTING stage artifact to expose the coverage percentage
 * {@link com.schwab.assessment.orchestration.PolicyGuardrail} checks against
 * the RELEASE_READINESS coverage-threshold policy.
 */
public interface CoverageReporting {

    double coveragePercentage();
}
