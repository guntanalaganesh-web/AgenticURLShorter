package com.schwab.assessment.service;

import com.schwab.assessment.config.ShortLinkProperties;
import com.schwab.assessment.model.ClickRecordedEvent;
import com.schwab.assessment.model.LinkCreatedEvent;
import com.schwab.assessment.model.ShortLink;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Core URL shortener domain logic: create, resolve, and soft-delete short
 * links, following a cache-aside strategy against Redis (write DB first,
 * then cache; evict on delete; re-cache on miss) as documented in
 * ADR-001. Redirect click recording is fire-and-forget via Kafka so the
 * hot redirect path never blocks on analytics writes (ADR-003).
 */
@Service
public class UrlService {

    private static final String CACHE_KEY_PREFIX = "shortlink:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

    private final ShortLinkRepository repository;
    private final UrlValidator urlValidator;
    private final ShortCodeGenerator codeGenerator;
    private final StringRedisTemplate redisTemplate;
    private final KafkaPublisher kafkaPublisher;
    private final ShortLinkProperties properties;

    public UrlService(ShortLinkRepository repository, UrlValidator urlValidator, ShortCodeGenerator codeGenerator,
                       StringRedisTemplate redisTemplate, KafkaPublisher kafkaPublisher,
                       ShortLinkProperties properties) {
        this.repository = repository;
        this.urlValidator = urlValidator;
        this.codeGenerator = codeGenerator;
        this.redisTemplate = redisTemplate;
        this.kafkaPublisher = kafkaPublisher;
        this.properties = properties;
    }

    @Transactional
    public ShortLink create(String originalUrl, String createdBy, Long ttlHours) {
        urlValidator.validate(originalUrl);

        String code = generateUniqueCode();
        long effectiveTtlHours = ttlHours != null ? ttlHours : properties.defaultTtlHours();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(effectiveTtlHours));

        ShortLink shortLink = new ShortLink(originalUrl, code, expiresAt, createdBy);
        repository.save(shortLink);

        cache(code, originalUrl);

        kafkaPublisher.publishLinkCreated(new LinkCreatedEvent(shortLink.getId(), code, originalUrl, createdBy,
                shortLink.getCreatedAt()));

        return shortLink;
    }

    /**
     * Resolves a short code to its original URL, checking Redis before
     * falling back to PostgreSQL and re-populating the cache on a miss.
     */
    public String resolve(String shortCode) {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + shortCode);
        if (cached != null) {
            return cached;
        }

        ShortLink shortLink = repository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new ShortLinkNotFoundException(shortCode));
        if (shortLink.isExpired()) {
            throw new ShortLinkExpiredException(shortCode);
        }

        cache(shortCode, shortLink.getOriginalUrl());
        return shortLink.getOriginalUrl();
    }

    @Transactional
    public void delete(String shortCode) {
        ShortLink shortLink = repository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new ShortLinkNotFoundException(shortCode));
        shortLink.deactivate();
        repository.save(shortLink);
        redisTemplate.delete(CACHE_KEY_PREFIX + shortCode);
    }

    /**
     * Fire-and-forget: publishes the click to Kafka rather than writing to
     * PostgreSQL on the redirect's request path. {@code AnalyticsService}
     * consumes the topic and performs the actual write.
     */
    public void recordClickAsync(String shortCode, String ipHash, String referrer, String userAgentHash,
                                  String countryCode) {
        kafkaPublisher.publishClickRecorded(
                new ClickRecordedEvent(shortCode, Instant.now(), ipHash, referrer, userAgentHash, countryCode));
    }

    public boolean isPermanentRedirect() {
        return properties.redirect().permanent();
    }

    private void cache(String code, String originalUrl) {
        redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + code, originalUrl, CACHE_TTL);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = codeGenerator.generate(properties.baseCodeLength());
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate a unique short code after " + MAX_CODE_GENERATION_ATTEMPTS + " attempts");
    }
}
