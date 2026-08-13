package com.schwab.assessment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.assessment.service.RateLimiterService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers cross-cutting web infrastructure: currently the sliding-window
 * rate limiter, applied to every request except infrastructure endpoints
 * (health checks, metrics, and API documentation).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public WebConfig(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimiterService, objectMapper))
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");
    }
}
