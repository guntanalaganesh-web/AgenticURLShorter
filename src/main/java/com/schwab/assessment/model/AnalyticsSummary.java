package com.schwab.assessment.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregate click analytics for one short link, returned by
 * {@code GET /api/v1/links/{code}/analytics}.
 */
public record AnalyticsSummary(
        String shortCode,
        long clickCount,
        long uniqueIpCount,
        List<ReferrerCount> topReferrers,
        Map<LocalDate, Long> clicksByDay
) {
}
