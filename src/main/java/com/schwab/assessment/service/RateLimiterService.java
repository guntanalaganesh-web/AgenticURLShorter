package com.schwab.assessment.service;

import com.schwab.assessment.config.RateLimiterProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Sliding-window rate limiter, per client IP, backed by a Redis ZSET whose
 * scores are request timestamps. On every check, entries older than the
 * one-minute window are evicted before counting, giving a true rolling
 * window rather than the burst-at-the-boundary behavior of fixed windows.
 */
@Service
public class RateLimiterService {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;
    private final RateLimiterProperties properties;

    public RateLimiterService(StringRedisTemplate redisTemplate, RateLimiterProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Checks whether {@code clientIp} is within its per-minute budget and,
     * if so, records this request against the window. If the budget is
     * already exhausted, the request is NOT recorded.
     */
    public RateLimitDecision checkAndRecord(String clientIp) {
        String key = KEY_PREFIX + clientIp;
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW.toMillis();
        int limit = properties.requestsPerMinute();

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.removeRangeByScore(key, 0, windowStart);

        Long currentCount = zSetOps.zCard(key);
        long count = currentCount == null ? 0 : currentCount;

        if (count >= limit) {
            long retryAfterSeconds = computeRetryAfterSeconds(key, now);
            return new RateLimitDecision(false, 0, retryAfterSeconds, limit);
        }

        zSetOps.add(key, UUID.randomUUID().toString(), now);
        redisTemplate.expire(key, WINDOW.plusSeconds(5));

        long remaining = Math.max(0, limit - count - 1);
        return new RateLimitDecision(true, remaining, 0, limit);
    }

    private long computeRetryAfterSeconds(String key, long now) {
        Set<ZSetOperations.TypedTuple<String>> oldest =
                redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
        if (oldest == null || oldest.isEmpty()) {
            return WINDOW.toSeconds();
        }
        double oldestScore = oldest.iterator().next().getScore();
        long windowResetAt = (long) oldestScore + WINDOW.toMillis();
        return Math.max(1, (windowResetAt - now + 999) / 1000);
    }

    /**
     * @param allowed          whether this request may proceed
     * @param remaining        requests remaining in the current window if allowed
     * @param retryAfterSeconds seconds until the oldest request ages out, if not allowed
     * @param limit            the configured per-minute budget
     */
    public record RateLimitDecision(boolean allowed, long remaining, long retryAfterSeconds, int limit) {
    }
}
