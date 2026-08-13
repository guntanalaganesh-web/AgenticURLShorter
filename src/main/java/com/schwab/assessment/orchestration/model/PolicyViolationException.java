package com.schwab.assessment.orchestration.model;

/**
 * Thrown by {@link com.schwab.assessment.orchestration.PolicyGuardrail} when
 * a stage is about to execute in violation of a governing policy rule
 * (e.g. an SSRF-prone URL, a hardcoded secret, or insufficient test
 * coverage). Carries the specific rule that was violated and a concrete
 * remediation hint so the failure is actionable, not just descriptive.
 */
public class PolicyViolationException extends RuntimeException {

    private final Stage stage;
    private final String rule;
    private final String remediationHint;

    public PolicyViolationException(Stage stage, String rule, String remediationHint) {
        super("Policy violation at stage " + stage + " [" + rule + "]: " + remediationHint);
        this.stage = stage;
        this.rule = rule;
        this.remediationHint = remediationHint;
    }

    public Stage getStage() {
        return stage;
    }

    public String getRule() {
        return rule;
    }

    public String getRemediationHint() {
        return remediationHint;
    }
}
