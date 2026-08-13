package com.schwab.assessment.service;

/**
 * Thrown when a short code has no active, non-expired {@code short_links} row.
 */
public class ShortLinkNotFoundException extends RuntimeException {

    public ShortLinkNotFoundException(String shortCode) {
        super("No active short link found for code: " + shortCode);
    }
}
