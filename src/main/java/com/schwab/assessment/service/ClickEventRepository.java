package com.schwab.assessment.service;

import com.schwab.assessment.model.ClickEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data access to the {@code click_events} table, including the
 * aggregate queries behind {@code GET /api/v1/links/{code}/analytics}.
 */
public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID> {

    long countByShortCode(String shortCode);

    @Query("SELECT COUNT(DISTINCT c.ipHash) FROM ClickEvent c WHERE c.shortCode = :shortCode")
    long countDistinctIpByShortCode(@Param("shortCode") String shortCode);

    @Query("SELECT c.referrer, COUNT(c) FROM ClickEvent c "
            + "WHERE c.shortCode = :shortCode AND c.referrer IS NOT NULL "
            + "GROUP BY c.referrer ORDER BY COUNT(c) DESC")
    List<Object[]> findTopReferrers(@Param("shortCode") String shortCode, Pageable pageable);

    @Query(value = "SELECT CAST(clicked_at AS date) AS day, COUNT(*) AS cnt "
            + "FROM click_events WHERE short_code = :shortCode AND clicked_at >= :since "
            + "GROUP BY CAST(clicked_at AS date) ORDER BY day", nativeQuery = true)
    List<Object[]> findClicksByDay(@Param("shortCode") String shortCode, @Param("since") Instant since);
}
