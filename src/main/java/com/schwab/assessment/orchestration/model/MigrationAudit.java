package com.schwab.assessment.orchestration.model;

/**
 * Implemented by an IMPLEMENTATION stage artifact so
 * {@link com.schwab.assessment.orchestration.PolicyGuardrail} can verify
 * database migration scripts were reviewed before RELEASE_READINESS is
 * allowed to proceed.
 */
public interface MigrationAudit {

    boolean migrationScriptsReviewed();

    int migrationCount();
}
