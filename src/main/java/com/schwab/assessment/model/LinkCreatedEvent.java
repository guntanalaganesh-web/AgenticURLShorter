package com.schwab.assessment.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka payload published by {@code UrlService} when a new short link is
 * created, carried on the {@code url-shortener.link-created} topic.
 */
public record LinkCreatedEvent(UUID linkId, String shortCode, String originalUrl, String createdBy,
                                Instant createdAt) {
}
