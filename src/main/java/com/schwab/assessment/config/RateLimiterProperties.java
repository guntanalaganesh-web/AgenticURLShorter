package com.schwab.assessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code rate-limiter.*}: the sliding-window request budget enforced
 * per client IP by {@link com.schwab.assessment.service.RateLimiterService}.
 */
@ConfigurationProperties(prefix = "rate-limiter")
public record RateLimiterProperties(int requestsPerMinute) {

    public RateLimiterProperties {
        if (requestsPerMinute <= 0) {
            requestsPerMinute = 100;
        }
    }
}
