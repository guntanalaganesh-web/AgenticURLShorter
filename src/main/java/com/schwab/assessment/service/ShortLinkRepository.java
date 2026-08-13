package com.schwab.assessment.service;

import com.schwab.assessment.model.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data access to the {@code short_links} table.
 */
public interface ShortLinkRepository extends JpaRepository<ShortLink, java.util.UUID> {

    Optional<ShortLink> findByShortCode(String shortCode);

    Optional<ShortLink> findByShortCodeAndActiveTrue(String shortCode);

    boolean existsByShortCode(String shortCode);
}
