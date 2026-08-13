package com.schwab.assessment.api;

/**
 * Request body for {@code POST /orchestration/gates/{stageId}/approve}. All
 * fields are optional: an empty body approves the pending gate as a demo
 * user; set {@code approved=false} to exercise the rejection path instead.
 */
public record GateDecisionRequest(Boolean approved, String approver, String reason) {

    public boolean isApproved() {
        return approved == null || approved;
    }

    public String approverOrDefault() {
        return (approver == null || approver.isBlank()) ? "demo-user" : approver;
    }

    public String reasonOrDefault() {
        return (reason == null || reason.isBlank()) ? "resolved via REST endpoint" : reason;
    }
}
