package com.schwab.assessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code orchestration.*} configuration tree: gate auto-approval
 * for demo mode, and the retry/backoff policy applied by
 * {@link com.schwab.assessment.orchestration.StageExecutor}.
 */
@ConfigurationProperties(prefix = "orchestration")
public record OrchestrationProperties(Gates gates, Retry retry) {

    public OrchestrationProperties {
        if (gates == null) {
            gates = new Gates(true);
        }
        if (retry == null) {
            retry = new Retry(3, 500L, 2.0);
        }
    }

    public record Gates(boolean autoApprove) {
    }

    public record Retry(int maxAttempts, long initialBackoffMs, double backoffMultiplier) {
    }
}
