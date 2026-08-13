package com.schwab.assessment.integration;

import com.schwab.assessment.api.ApiResponse;
import com.schwab.assessment.api.CreateLinkRequest;
import com.schwab.assessment.api.LinkResponse;
import com.schwab.assessment.model.AnalyticsSummary;
import com.schwab.assessment.model.ShortLink;
import com.schwab.assessment.service.ShortLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end create -> redirect -> analytics -> delete flow against real
 * PostgreSQL, Redis, and Kafka (via {@link AbstractIntegrationTest}),
 * proving the cache-aside and async-click-recording designs actually work
 * together, not just in isolation.
 */
class UrlShortenerIntegrationTest extends AbstractIntegrationTest {

    private static final String TARGET_URL = "https://example.com/integration-test-target";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @Test
    void createRedirectAnalyticsAndDeleteFlow() {
        LinkResponse created = createLink(TARGET_URL);
        String shortCode = created.shortCode();
        assertThat(shortCode).hasSize(8);
        assertThat(created.active()).isTrue();

        ResponseEntity<Void> redirectResponse =
                restTemplate.exchange("/" + shortCode, HttpMethod.GET, null, Void.class);
        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResponse.getHeaders().getLocation()).isEqualTo(URI.create(TARGET_URL));

        // click recording is async via Kafka; poll analytics until the consumer catches up
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            AnalyticsSummary analytics = getAnalytics(shortCode);
            assertThat(analytics.clickCount()).isEqualTo(1);
        });

        AnalyticsSummary analytics = getAnalytics(shortCode);
        assertThat(analytics.shortCode()).isEqualTo(shortCode);
        assertThat(analytics.clickCount()).isEqualTo(1);
        assertThat(analytics.uniqueIpCount()).isEqualTo(1);

        ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                "/api/v1/links/" + shortCode, HttpMethod.DELETE, null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> redirectAfterDelete =
                restTemplate.exchange("/" + shortCode, HttpMethod.GET, null, Void.class);
        assertThat(redirectAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsSsrfRiskUrlAtCreationTime() {
        CreateLinkRequest request = new CreateLinkRequest("http://169.254.169.254/latest/meta-data/", null, "test");
        ResponseEntity<ApiResponse<LinkResponse>> response = restTemplate.exchange(
                "/api/v1/links", HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<LinkResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("INVALID_URL");
    }

    @Test
    void secondRedirectIsServedFromCacheEvenAfterTheDbRowIsRemoved() {
        LinkResponse created = createLink(TARGET_URL);
        String shortCode = created.shortCode();

        // First redirect: UrlService.resolve() misses Redis, reads Postgres,
        // and re-populates the cache (see UrlService.resolve()/cache()).
        ResponseEntity<Void> firstRedirect =
                restTemplate.exchange("/" + shortCode, HttpMethod.GET, null, Void.class);
        assertThat(firstRedirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        // Remove the DB row directly (bypassing UrlService/its cache eviction),
        // so a second FOUND response can only mean it came from Redis, not Postgres.
        ShortLink persisted = shortLinkRepository.findByShortCode(shortCode).orElseThrow();
        shortLinkRepository.delete(persisted);

        ResponseEntity<Void> secondRedirect =
                restTemplate.exchange("/" + shortCode, HttpMethod.GET, null, Void.class);
        assertThat(secondRedirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(secondRedirect.getHeaders().getLocation()).isEqualTo(URI.create(TARGET_URL));
    }

    @Test
    void redirectForAnUnknownShortCodeReturns404() {
        ResponseEntity<Void> response =
                restTemplate.exchange("/does-not-exist", HttpMethod.GET, null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteSoftDeletesTheLinkAndEvictsItFromCache() {
        LinkResponse created = createLink(TARGET_URL);
        String shortCode = created.shortCode();
        String cacheKey = "shortlink:" + shortCode;

        // Populate the cache the same way the redirect path would.
        restTemplate.exchange("/" + shortCode, HttpMethod.GET, null, Void.class);
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                "/api/v1/links/" + shortCode, HttpMethod.DELETE, null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        Optional<ShortLink> stillPresentButInactive = shortLinkRepository.findByShortCode(shortCode);
        assertThat(stillPresentButInactive).isPresent();
        assertThat(stillPresentButInactive.get().isActive()).isFalse();
        assertThat(shortLinkRepository.findByShortCodeAndActiveTrue(shortCode)).isEmpty();
    }

    private LinkResponse createLink(String url) {
        CreateLinkRequest request = new CreateLinkRequest(url, null, "integration-test");
        ResponseEntity<ApiResponse<LinkResponse>> response = restTemplate.exchange(
                "/api/v1/links", HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<LinkResponse>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }

    private AnalyticsSummary getAnalytics(String shortCode) {
        ResponseEntity<ApiResponse<AnalyticsSummary>> response = restTemplate.exchange(
                "/api/v1/links/" + shortCode + "/analytics", HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<AnalyticsSummary>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().data();
    }
}
