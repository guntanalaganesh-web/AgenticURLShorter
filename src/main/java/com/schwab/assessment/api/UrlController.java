package com.schwab.assessment.api;

import com.schwab.assessment.model.AnalyticsSummary;
import com.schwab.assessment.model.ShortLink;
import com.schwab.assessment.service.AnalyticsService;
import com.schwab.assessment.service.HashingUtil;
import com.schwab.assessment.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * The URL shortener's public API: create, redirect, analytics, and
 * soft-delete for short links. This is the substrate the orchestration
 * engine (see {@link OrchestrationController}) is being evaluated on
 * building and evolving.
 */
@RestController
@Tag(name = "URL Shortener", description = "Create, resolve, and manage short links")
public class UrlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    public UrlController(UrlService urlService, AnalyticsService analyticsService) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "Create a short link", description = "Validates the URL (format + SSRF checks), "
            + "mints an 8-character code, caches it in Redis, and publishes a link-created Kafka event.")
    @PostMapping("/api/v1/links")
    public ResponseEntity<ApiResponse<LinkResponse>> createLink(@Valid @RequestBody CreateLinkRequest request,
                                                                  HttpServletRequest servletRequest) {
        String createdBy = (request.createdBy() == null || request.createdBy().isBlank())
                ? "anonymous" : request.createdBy();
        ShortLink shortLink = urlService.create(request.url(), createdBy, request.ttlHours());

        String shortUrl = ServletUriComponentsBuilder.fromContextPath(servletRequest)
                .path("/{code}")
                .buildAndExpand(shortLink.getShortCode())
                .toUriString();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(LinkResponse.from(shortLink, shortUrl)));
    }

    @Operation(summary = "Redirect to the original URL",
            description = "Checks Redis first, falls back to PostgreSQL on a cache miss, records the click "
                    + "asynchronously via Kafka, and redirects with a 301 or 302 depending on "
                    + "shortlink.redirect.permanent.")
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@Parameter(description = "8-character short code") @PathVariable String code,
                                          HttpServletRequest servletRequest) {
        String originalUrl = urlService.resolve(code);

        String ipHash = HashingUtil.sha256Hex(servletRequest.getRemoteAddr());
        String userAgentHash = HashingUtil.sha256Hex(servletRequest.getHeader("User-Agent"));
        String referrer = servletRequest.getHeader("Referer");
        urlService.recordClickAsync(code, ipHash, referrer, userAgentHash, null);

        HttpStatus status = urlService.isPermanentRedirect() ? HttpStatus.MOVED_PERMANENTLY : HttpStatus.FOUND;
        return ResponseEntity.status(status).location(URI.create(originalUrl)).build();
    }

    @Operation(summary = "Get click analytics",
            description = "Click count, unique IPs, top referrers, and clicks-by-day for the last 30 days.")
    @GetMapping("/api/v1/links/{code}/analytics")
    public ResponseEntity<ApiResponse<AnalyticsSummary>> getAnalytics(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getAnalytics(code)));
    }

    @Operation(summary = "Soft-delete a short link", description = "Marks the link inactive and evicts it from Redis.")
    @DeleteMapping("/api/v1/links/{code}")
    public ResponseEntity<ApiResponse<Void>> deleteLink(@PathVariable String code) {
        urlService.delete(code);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
