package com.schwab.assessment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.assessment.service.RateLimiterService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers cross-cutting web infrastructure: the sliding-window rate
 * limiter (applied to every request except infrastructure endpoints), and
 * CORS for the orchestration dashboard, which runs on Vite's dev server
 * origin -- a different origin than the API, so the browser enforces CORS
 * on every request unless explicitly allowed here.
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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
