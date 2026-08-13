package com.schwab.assessment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/links}.
 *
 * @param url       the URL to shorten; validated for format and SSRF risk before a code is minted
 * @param ttlHours  optional time-to-live override; defaults to {@code shortlink.default-ttl-hours} when omitted
 * @param createdBy optional attribution; defaults to "anonymous" when omitted
 */
public record CreateLinkRequest(
        @NotBlank(message = "url must not be blank")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,

        @Positive(message = "ttlHours must be positive")
        Long ttlHours,

        String createdBy
) {
}
