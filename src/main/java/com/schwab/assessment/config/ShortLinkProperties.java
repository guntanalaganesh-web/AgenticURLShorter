package com.schwab.assessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds {@code shortlink.*}: short-code generation length, default
 * time-to-live, redirect status behavior, and the SSRF-prevention
 * allow/block lists used by {@link com.schwab.assessment.service.UrlValidator}.
 */
@ConfigurationProperties(prefix = "shortlink")
public record ShortLinkProperties(
        int baseCodeLength,
        long defaultTtlHours,
        Redirect redirect,
        List<String> allowedUrlSchemes,
        List<String> blockedHostPatterns
) {

    public ShortLinkProperties {
        if (baseCodeLength <= 0) {
            baseCodeLength = 8;
        }
        if (defaultTtlHours <= 0) {
            defaultTtlHours = 8760;
        }
        if (redirect == null) {
            redirect = new Redirect(false);
        }
        if (allowedUrlSchemes == null) {
            allowedUrlSchemes = List.of("http", "https");
        }
        if (blockedHostPatterns == null) {
            blockedHostPatterns = List.of("localhost", "127.0.0.1", "0.0.0.0");
        }
    }

    public record Redirect(boolean permanent) {
    }
}
