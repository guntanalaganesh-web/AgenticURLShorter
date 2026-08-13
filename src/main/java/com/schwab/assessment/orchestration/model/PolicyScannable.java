package com.schwab.assessment.orchestration.model;

/**
 * Implemented by stage artifacts (typically from the scenario package) that
 * want {@link com.schwab.assessment.orchestration.PolicyGuardrail} to scan
 * their content for policy red flags -- e.g. a task plan whose descriptions
 * mention hardcoding a secret. Keeps the orchestration package free of any
 * compile-time dependency on concrete artifact types while still letting it
 * inspect them.
 */
public interface PolicyScannable {

    String policyScanText();
}
