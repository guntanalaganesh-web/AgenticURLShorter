package com.schwab.assessment.api;

import com.schwab.assessment.model.ShortLink;

import java.time.Instant;

/**
 * Response body describing a short link, returned from create and lookup endpoints.
 */
public record LinkResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        boolean active
) {

    public static LinkResponse from(ShortLink shortLink, String shortUrl) {
        return new LinkResponse(shortLink.getShortCode(), shortUrl, shortLink.getOriginalUrl(),
                shortLink.getCreatedAt(), shortLink.getExpiresAt(), shortLink.isActive());
    }
}
