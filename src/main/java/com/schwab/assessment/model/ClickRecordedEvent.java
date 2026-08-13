package com.schwab.assessment.model;

import java.time.Instant;

/**
 * Kafka payload published by the redirect handler on every click, carried
 * on the {@code url-shortener.link-clicked} topic and consumed by
 * {@code AnalyticsService} to persist a {@link ClickEvent} asynchronously,
 * off the redirect's request path.
 */
public record ClickRecordedEvent(String shortCode, Instant clickedAt, String ipHash, String referrer,
                                  String userAgentHash, String countryCode) {
}
