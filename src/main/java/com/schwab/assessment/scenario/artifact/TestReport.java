package com.schwab.assessment.scenario.artifact;

import com.schwab.assessment.orchestration.model.CoverageReporting;

/**
 * TESTING stage output: pass/fail counts and coverage percentage.
 * Implements {@link CoverageReporting} so {@link com.schwab.assessment.orchestration.PolicyGuardrail}
 * can enforce the RELEASE_READINESS coverage threshold.
 */
public record TestReport(int passCount, int failCount, double coveragePercentage) implements CoverageReporting {
}
