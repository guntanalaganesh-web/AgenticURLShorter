package com.schwab.assessment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code short_links} table: one row per shortened URL,
 * the core entity of the URL shortener service the orchestration engine
 * builds and manages.
 */
@Entity
@Table(name = "short_links")
public class ShortLink {

    @Id
    private UUID id;

    @Column(name = "original_url", nullable = false, columnDefinition = "text")
    private String originalUrl;

    @Column(name = "short_code", nullable = false, unique = true, length = 8)
    private String shortCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected ShortLink() {
        // required by JPA
    }

    public ShortLink(String originalUrl, String shortCode, Instant expiresAt, String createdBy) {
        this.id = UUID.randomUUID();
        this.originalUrl = originalUrl;
        this.shortCode = shortCode;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public void deactivate() {
        this.active = false;
    }
}
