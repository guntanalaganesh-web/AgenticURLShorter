package com.schwab.assessment.service;

/**
 * Thrown when a short code resolves to a row whose {@code expires_at} has
 * already passed.
 */
public class ShortLinkExpiredException extends RuntimeException {

    public ShortLinkExpiredException(String shortCode) {
        super("Short link has expired: " + shortCode);
    }
}
