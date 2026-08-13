package com.schwab.assessment.model;

/**
 * One entry in a short link's top-referrers breakdown.
 */
public record ReferrerCount(String referrer, long count) {
}
