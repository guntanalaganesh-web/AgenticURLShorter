package com.schwab.assessment.scenario.artifact;

import com.schwab.assessment.orchestration.model.MigrationAudit;

import java.util.List;

/**
 * IMPLEMENTATION stage output: classes created, tests written, and Flyway
 * migrations introduced. Implements {@link MigrationAudit} so
 * {@link com.schwab.assessment.orchestration.PolicyGuardrail} can verify
 * migrations were reviewed before RELEASE_READINESS.
 */
public record ImplementationSummary(
        List<String> classesCreated,
        int testCount,
        int migrationCount,
        boolean migrationScriptsReviewed
) implements MigrationAudit {
}
