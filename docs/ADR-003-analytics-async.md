# ADR-003: Kafka for Asynchronous Click Event Processing

## Status

Accepted

## Context

Every redirect (`GET /{code}`) should result in a click being recorded for analytics -- click count,
unique IPs, top referrers, clicks by day. But the redirect itself is the service's hottest, most
latency-sensitive path (see [ADR-001](ADR-001-caching-strategy.md)). Writing a `click_events` row
synchronously, in the same request that serves the redirect, ties redirect latency to a database write and
means a slow or momentarily unavailable database directly delays every redirect in flight.

## Decision

Publish a `ClickRecordedEvent` to Kafka (`url-shortener.link-clicked` topic) from the redirect handler
instead of writing to PostgreSQL synchronously. A separate `@KafkaListener` in `AnalyticsService` consumes
the topic and performs the actual `click_events` insert. The redirect response is returned as soon as the
Kafka publish is initiated (`KafkaTemplate.send` is asynchronous; the handler does not block on the
broker ack).

Events are keyed by short code, so all events for a given link land on the same partition and are
processed in order relative to each other.

`LinkCreatedEvent` (published on `POST /api/v1/links`) follows the same pattern on the
`url-shortener.link-created` topic, for the same reason: link creation shouldn't be slowed down by
whatever downstream system might eventually want to react to new links.

## Consequences

**Positive:**
- The redirect's request-response cycle never waits on a database write; the only work done
  synchronously is a cache lookup and an async Kafka send.
- A burst of redirects becomes a burst of Kafka messages, which Kafka is built to absorb, rather than a
  burst of concurrent database writes, which PostgreSQL is not.
- Click and link-created events are available to any other consumer that wants them later (e.g. a
  fraud-detection service, a data warehouse sink) without touching the redirect path at all.

**Negative:**
- Click analytics are eventually consistent, not immediate: a click recorded a few milliseconds ago may
  not yet be reflected in `GET /api/v1/links/{code}/analytics` if the consumer hasn't caught up. This is
  an explicit, acceptable trade-off for an analytics feature, and is called out in
  `UrlShortenerIntegrationTest`, which polls (via Awaitility) rather than asserting immediately after a
  redirect.
- Introduces an operational dependency on Kafka (and ZooKeeper, in this Kafka version) that a purely
  synchronous design wouldn't have.
- If the consumer falls behind or the topic isn't being consumed, click data silently doesn't show up in
  analytics rather than the create/redirect path failing loudly -- acceptable for analytics, would not be
  acceptable if this pattern were reused for anything where the write is load-bearing (e.g. billing).

## Alternatives Considered

- **Synchronous write to `click_events` in the redirect handler:** rejected -- directly couples redirect
  latency to database write latency on the system's hottest path.
- **`@Async` Spring method instead of Kafka:** rejected -- decouples the write from the request thread, but
  the work still executes in-process; a burst of redirects still becomes a burst of concurrent database
  writes (just on a thread pool instead of the request thread), and in-flight async work is lost on app
  restart. Kafka additionally gives durability and lets other consumers subscribe to the same event stream.
- **Batch/scheduled aggregation instead of per-click events:** rejected -- loses per-click fidelity (unique
  IPs, individual referrers) that the analytics endpoint needs, in exchange for a write-volume reduction
  that async Kafka publishing already achieves without that trade-off.
