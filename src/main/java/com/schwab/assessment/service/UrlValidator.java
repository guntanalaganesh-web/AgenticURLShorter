package com.schwab.assessment.service;

import com.schwab.assessment.config.ShortLinkProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Validates URLs submitted to {@code POST /api/v1/links} before a short
 * code is minted for them: well-formed absolute URI, an allowed scheme, and
 * a host that is not loopback, link-local, or a private address range --
 * the classic SSRF target set (including the cloud metadata endpoint
 * {@code 169.254.169.254}).
 */
@Component
public class UrlValidator {

    private final ShortLinkProperties properties;

    public UrlValidator(ShortLinkProperties properties) {
        this.properties = properties;
    }

    public void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("URL must not be blank");
        }

        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is not a valid URI: " + rawUrl);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !properties.allowedUrlSchemes().contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new InvalidUrlException("URL scheme must be one of " + properties.allowedUrlSchemes()
                    + ", got: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must include a host: " + rawUrl);
        }

        if (isBlockedHost(host)) {
            throw new InvalidUrlException("URL targets a disallowed host (loopback/internal/private range): " + host);
        }
    }

    private boolean isBlockedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String pattern : properties.blockedHostPatterns()) {
            String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
            if (normalizedPattern.endsWith(".")) {
                if (normalized.startsWith(normalizedPattern)) {
                    return true;
                }
            } else if (normalized.equals(normalizedPattern)) {
                return true;
            }
        }
        return false;
    }
}
