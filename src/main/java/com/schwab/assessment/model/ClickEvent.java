package com.schwab.assessment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code click_events} table: one row per redirect
 * served for a short link, used to compute analytics. Client IP and user
 * agent are stored only as SHA-256 hashes -- never in raw form -- per this
 * service's PII policy.
 */
@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    private UUID id;

    @Column(name = "short_code", nullable = false, length = 8)
    private String shortCode;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @Column(name = "referrer", columnDefinition = "text")
    private String referrer;

    @Column(name = "user_agent_hash", length = 64)
    private String userAgentHash;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    protected ClickEvent() {
        // required by JPA
    }

    public ClickEvent(String shortCode, String ipHash, String referrer, String userAgentHash, String countryCode) {
        this.id = UUID.randomUUID();
        this.shortCode = shortCode;
        this.clickedAt = Instant.now();
        this.ipHash = ipHash;
        this.referrer = referrer;
        this.userAgentHash = userAgentHash;
        this.countryCode = countryCode;
    }

    public UUID getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getIpHash() {
        return ipHash;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getUserAgentHash() {
        return userAgentHash;
    }

    public String getCountryCode() {
        return countryCode;
    }
}
