# ADR-001: Redis Cache-Aside for Hot URL Redirects

## Status

Accepted

## Context

`GET /{code}` is the highest-traffic, most latency-sensitive endpoint in the service -- every redirect a
short link ever serves goes through it. A naive implementation queries PostgreSQL on every request. Under
load, that puts every redirect on the critical path of a database round trip, and makes the redirect
endpoint's latency and throughput a direct function of the database's.

We need a caching strategy for `short_code -> original_url` lookups that is simple to reason about,
doesn't require a cache-warming step, and degrades safely if Redis is briefly unavailable.

## Decision

Use **cache-aside** (lazy loading) with Redis as the cache:

- **Read (`GET /{code}`):** check Redis first (`shortlink:{code}`). On a hit, return immediately -- no
  database access. On a miss, read from PostgreSQL, populate Redis with a 1-hour TTL, then return.
- **Write (`POST /api/v1/links`):** write to PostgreSQL first (the durable source of truth), then populate
  Redis. If a link is looked up before this write completes, that's a cache miss handled by the normal
  read path -- there is no inconsistency window where Redis has stale data, only a brief window where it
  has no data yet.
- **Delete (`DELETE /api/v1/links/{code}`):** mark the row inactive in PostgreSQL, then evict the Redis key
  immediately. Evicting after the DB write (not before) means a concurrent read that hits the DB during
  the delete still sees a consistent state either way.

This is implemented in `UrlService` and documented in code as the concrete cache key, TTL, and ordering
described above.

## Consequences

**Positive:**
- Simple mental model: Redis is always allowed to be empty; PostgreSQL is always authoritative.
- No cache-warming or precomputation step needed at startup.
- A Redis outage degrades to "every redirect hits Postgres" rather than serving errors -- the read path
  falls through to the database on any cache miss, including one caused by Redis being down (as long as
  the client library's failure mode surfaces as a miss/exception the service can catch; in production this
  would need an explicit circuit breaker around the Redis call, which is out of scope for this assessment
  but is called out as a known limitation in `engineering-summary.md`).
- A 1-hour TTL bounds staleness for links that get soft-deleted through a path that somehow skips the
  explicit eviction (defense in depth).

**Negative:**
- First request for any given code always costs a full database round trip (no cache-warming).
- Two network calls (Redis, then Postgres) on every cache miss, versus one call in a DB-only design.
- Requires operating a second stateful service (Redis) in production.

## Alternatives Considered

- **Write-through cache** (write to Redis and Postgres together, atomically or as a transaction):
  rejected. It adds complexity (what happens if the Redis write succeeds but the Postgres write fails, or
  vice versa?) for a benefit -- avoiding first-request cache misses -- that doesn't matter much for a
  service where links are created once and read many times.
- **No cache, PostgreSQL only:** rejected as the baseline that motivated this ADR; doesn't meet the
  latency goal for the hottest path in the system.
- **In-process local cache (e.g. Caffeine) instead of Redis:** rejected because it doesn't work across
  multiple app instances without an invalidation broadcast mechanism, which is strictly more complex than
  using a shared cache in the first place.
