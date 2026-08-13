package com.schwab.assessment.service;

import com.schwab.assessment.config.RateLimiterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Exercises the sliding-window algorithm against an in-memory fake of the
 * Redis ZSET, since real Redis is reserved for the Testcontainers
 * integration tests.
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    /** Fake Redis ZSETs, keyed by Redis key (e.g. "ratelimit:203.0.113.5"), each mapping member -> score. */
    private final Map<String, TreeMap<String, Double>> zsetsByKey = new HashMap<>();
    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            double min = invocation.getArgument(1);
            double max = invocation.getArgument(2);
            windowFor(key).entrySet().removeIf(e -> e.getValue() >= min && e.getValue() <= max);
            return null;
        }).when(zSetOperations).removeRangeByScore(anyString(), anyDouble(), anyDouble());

        lenient().when(zSetOperations.zCard(anyString()))
                .thenAnswer(invocation -> (long) windowFor(invocation.getArgument(0)).size());

        lenient().when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String member = invocation.getArgument(1);
            double score = invocation.getArgument(2);
            windowFor(key).put(member, score);
            return true;
        });

        lenient().when(redisTemplate.expire(anyString(), eq(Duration.ofMinutes(1).plusSeconds(5))))
                .thenReturn(true);

        lenient().when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(0L))).thenAnswer(invocation -> {
            Map<String, Double> window = windowFor(invocation.getArgument(0));
            return window.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .<Set<ZSetOperations.TypedTuple<String>>>map(
                            oldest -> Set.of(new DefaultTypedTuple<>(oldest.getKey(), oldest.getValue())))
                    .orElseGet(Set::of);
        });

        service = new RateLimiterService(redisTemplate, new RateLimiterProperties(100));
    }

    private Map<String, Double> windowFor(String key) {
        return zsetsByKey.computeIfAbsent(key, k -> new TreeMap<>());
    }

    @Test
    void allowsExactlyTheConfiguredLimitThenBlocksTheNextRequest() {
        for (int i = 0; i < 100; i++) {
            RateLimiterService.RateLimitDecision decision = service.checkAndRecord("203.0.113.5");
            assertTrue(decision.allowed(), "request " + (i + 1) + " should be allowed");
        }

        RateLimiterService.RateLimitDecision blocked = service.checkAndRecord("203.0.113.5");

        assertFalse(blocked.allowed());
        assertEquals(100, blocked.limit());
        assertTrue(blocked.retryAfterSeconds() > 0);
    }

    @Test
    void windowExpiry_allowsNewRequestsOnceOldEntriesAgeOut() {
        // RateLimiterService has no injectable Clock -- it calls
        // System.currentTimeMillis() directly -- so there's no fake-clock seam
        // to advance (Option A from the task doesn't apply here). Rather than
        // stub zCard() to return 0 (Option B), seed the fake ZSET directly
        // with entries already outside the 60s window: checkAndRecord's own
        // removeRangeByScore(key, 0, windowStart) call is what evicts them,
        // so this exercises the real eviction path instead of bypassing it.
        String clientIp = "203.0.113.99";
        String key = "ratelimit:" + clientIp;
        double sixtyOneSecondsAgo = System.currentTimeMillis() - Duration.ofSeconds(61).toMillis();
        for (int i = 0; i < 100; i++) {
            windowFor(key).put("expired-" + i, sixtyOneSecondsAgo);
        }

        RateLimiterService.RateLimitDecision decision = service.checkAndRecord(clientIp);

        assertTrue(decision.allowed(), "a request should be allowed once every prior entry has aged out of the window");
    }

    @Test
    void tracksSeparateClientsIndependently() {
        for (int i = 0; i < 100; i++) {
            assertTrue(service.checkAndRecord("203.0.113.5").allowed());
        }
        assertFalse(service.checkAndRecord("203.0.113.5").allowed());

        // a different client IP has its own independent window
        assertTrue(service.checkAndRecord("198.51.100.9").allowed());
    }
}
