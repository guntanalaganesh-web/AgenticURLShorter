package com.schwab.assessment.service;

import com.schwab.assessment.model.AnalyticsSummary;
import com.schwab.assessment.model.ClickEvent;
import com.schwab.assessment.model.ClickRecordedEvent;
import com.schwab.assessment.model.ReferrerCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumes click events from Kafka and serves aggregate analytics.
 * Persisting clicks off the redirect's request path (ADR-003) means a burst
 * of redirects never has to wait on a database write.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final int TOP_REFERRERS_LIMIT = 5;
    private static final int CLICKS_BY_DAY_WINDOW_DAYS = 30;

    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    @KafkaListener(topics = KafkaTopics.LINK_CLICKED, groupId = "${spring.kafka.consumer.group-id}")
    public void consumeClickEvent(ClickRecordedEvent event) {
        clickEventRepository.save(new ClickEvent(event.shortCode(), event.ipHash(), event.referrer(),
                event.userAgentHash(), event.countryCode()));
        log.debug("Recorded click for {}", event.shortCode());
    }

    public AnalyticsSummary getAnalytics(String shortCode) {
        long clickCount = clickEventRepository.countByShortCode(shortCode);
        long uniqueIpCount = clickEventRepository.countDistinctIpByShortCode(shortCode);

        List<ReferrerCount> topReferrers = clickEventRepository
                .findTopReferrers(shortCode, PageRequest.of(0, TOP_REFERRERS_LIMIT)).stream()
                .map(row -> new ReferrerCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        Instant since = Instant.now().minus(Duration.ofDays(CLICKS_BY_DAY_WINDOW_DAYS));
        Map<LocalDate, Long> clicksByDay = new LinkedHashMap<>();
        for (Object[] row : clickEventRepository.findClicksByDay(shortCode, since)) {
            clicksByDay.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }

        return new AnalyticsSummary(shortCode, clickCount, uniqueIpCount, topReferrers, clicksByDay);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.parse(value.toString());
    }
}
