package com.schwab.assessment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.assessment.api.ApiResponse;
import com.schwab.assessment.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces {@link RateLimiterProperties} per client IP on every intercepted
 * request. On breach, responds 429 with a {@code Retry-After} header and an
 * {@link ApiResponse} error body instead of letting the request reach its
 * handler.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String clientIp = resolveClientIp(request);
        RateLimiterService.RateLimitDecision decision = rateLimiterService.checkAndRecord(clientIp);

        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType("application/json");
            ApiResponse<Void> body = ApiResponse.error("RATE_LIMIT_EXCEEDED",
                    "Too many requests from this client. Retry after " + decision.retryAfterSeconds() + "s.");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }
        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "unknown" : remoteAddr;
    }
}
