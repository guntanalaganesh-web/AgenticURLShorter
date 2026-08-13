# ADR-002: Sliding Window Rate Limiting via Redis ZSET

## Status

Accepted

## Context

The service needs to protect itself from abusive or accidental request floods -- both on the public
redirect endpoint and the link-creation API. The rate limiter must work correctly across multiple app
instances (state can't live in process memory), must not allow a burst at window boundaries, and needs to
tell a throttled client how long to wait before retrying.

This decision also stands in for the resolution of the ambiguous requirement "Add rate limiting" explored
in the Ambiguous scenario (`AmbiguousScenario.java`): scope (per-IP), algorithm (sliding window), and
default limit (100 req/min) are all decided here and recorded as `DecisionRecord`/`AmbiguityRecord` entries
during that scenario's REQUIREMENTS stage.

## Decision

Implement a **sliding-window log** per client IP using a Redis **ZSET**, where the score of each member is
the request's timestamp in epoch milliseconds:

1. On each request, remove ZSET entries older than `now - 1 minute` (`ZREMRANGEBYSCORE`).
2. Count remaining entries (`ZCARD`). If the count is at or above the configured limit
   (`rate-limiter.requests-per-minute`, default 100), reject with `429` and a `Retry-After` header computed
   from the oldest remaining entry's age.
3. Otherwise, add a new entry scored at the current timestamp (`ZADD`) and allow the request.

This is implemented in `RateLimiterService`, applied globally via a `HandlerInterceptor`
(`RateLimitInterceptor`), and scoped per client IP (`ratelimit:{ip}` key).

## Consequences

**Positive:**
- True sliding window: a client can never get more than the configured limit in *any* trailing 60-second
  window, unlike fixed windows which allow up to 2x the limit across a window boundary.
- Stateless across app instances -- any instance can serve any request because the counter lives in Redis,
  not local memory.
- `Retry-After` is computed from real data (the oldest entry's age), not a fixed guess.

**Negative:**
- O(log n) per operation and a ZSET entry per request per client, versus O(1) for a fixed-window counter.
  At 100 req/min per IP this is a trivial cost, but it doesn't scale to arbitrarily high per-key limits
  without reconsidering the approach (e.g. a coarser bucketed sliding window).
- Depends on Redis being available; a Redis outage currently fails open only in the sense that an
  exception from the interceptor would need explicit handling to avoid blocking all traffic -- this is the
  same known limitation noted in ADR-001 for the cache-aside path.

## Alternatives Considered

- **Fixed window counter** (increment a counter keyed by `ip:minute-bucket`, expire after 60s): rejected --
  simplest and cheapest option, but allows a client to send up to 2x the limit by timing requests around a
  window boundary (e.g. 100 requests at 0:59, another 100 at 1:00).
- **Token bucket:** rejected for this requirement specifically because "Add rate limiting" didn't specify
  a burst-capacity parameter, and token bucket's main advantage over sliding window is exactly that
  configurable burst allowance. Sliding window needs no extra parameter to behave predictably, which made
  it the safer default under an ambiguous requirement (see `AmbiguousScenario`).
- **Per-user scope instead of per-IP:** rejected because this service has no authentication layer:
  per-user limiting would require identifying users first, which is out of scope for the current
  requirement and would need its own design decision if added later.
- **Global limit (one bucket for the whole service):** rejected -- a single abusive client would degrade
  service for every other client, which defeats the purpose of the limiter.
