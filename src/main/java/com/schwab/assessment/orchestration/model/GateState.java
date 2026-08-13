package com.schwab.assessment.orchestration.model;

/**
 * State of a human approval checkpoint enforced by
 * {@link com.schwab.assessment.orchestration.GateKeeper} before a
 * high-impact stage is allowed to execute.
 */
public enum GateState {
    PENDING,
    APPROVED,
    REJECTED
}
