# Engineering Summary

## Plan and rationale

The brief frames this assessment around one primary artifact -- an agentic orchestration engine -- with a
URL shortener as the substrate it operates on. That ordering drove the build order (see the numbered
execution plan the code was built against, in-order, with a compile check after every step):

1. Project scaffold, infra config, and DB migrations first, so every later step had somewhere to compile
   and persist against.
2. The orchestration engine itself (`DependencyGraph` -> `ContextStore` -> `PolicyGuardrail` ->
   `StageExecutor` -> `GateKeeper` -> `ObservabilityCollector` -> `OrchestrationEngine`), built and
   compile-checked bottom-up so each collaborator existed before the thing that coordinates them.
3. The URL shortener domain (entities, rate limiting, services, controllers) next, as the concrete system
   the scenarios would exercise.
4. The three scenarios last, since they depend on both the engine and the domain, and are where the
   engine's behavior (parallel execution, retry/rollback, dynamic re-planning, gates) actually becomes
   observable.
5. Tests and documentation throughout the tail end, verified by actually running the unit test suite
   (13 tests, all green) and a full `mvn clean package`, not just by writing code that looked plausible.

The guiding design principle was **the orchestration engine should be self-contained and genuinely
reusable** -- it has no compile-time dependency on the URL shortener or on any scenario's concrete
artifact types. Extension points are interfaces the engine defines (`StageHandler`, `PolicyScannable`,
`CoverageReporting`, `OpenApiPublished`, `MigrationAudit`); scenarios implement them. See
`docs/architecture-overview.md` for how this plays out mechanically.

## Artifacts produced

- **Orchestration engine** (`orchestration/`): 7-stage DAG with parallel execution, bounded retry with
  exponential backoff, checkpoint/rollback, human approval gates, per-stage policy guardrails, a
  Postgres-backed decision/context store, and Micrometer-backed reliability metrics persisted per run.
- **URL shortener service** (`service/`, `api/`, `model/`): create/redirect/analytics/delete, Redis
  cache-aside, Redis-ZSET sliding-window rate limiting, Kafka-based async click/link-created events,
  SHA-256-hashed IP/user-agent storage, SSRF-aware URL validation.
- **Three scenarios** (`scenario/`): greenfield (full pipeline, both gates), brownfield (impact analysis
  pre-stage + a deliberate, recoverable migration-collision failure), ambiguous (three surfaced-and-resolved
  ambiguities + a live dynamic re-plan mid-run).
- **13 unit tests** (JUnit 5 + Mockito, no containers, run in under 3 seconds) covering dependency-graph
  topology/parallelism/cycle-detection, stage-executor retry/rollback, policy-guardrail SSRF/PII rules, and
  rate-limiter sliding-window behavior against a stateful in-memory ZSET fake.
- **2 integration test classes** (Testcontainers: real Postgres, Redis, Kafka) covering the full
  create-redirect-analytics-delete flow and an end-to-end greenfield pipeline run with persisted decision
  lineage.
- **Docker Compose stack** with healthchecks gating startup order, and a multi-stage `Dockerfile`.
- **This documentation set**: README, architecture overview, 3 ADRs, and this summary.

## Risks, trade-offs, and validation approach

- **Everything that could be compiled, was compiled, after every step.** All 79 main-source files and both
  test source sets build cleanly under JDK 17 / Maven (via the vendored wrapper). This was verified
  incrementally, not just at the end, specifically so a compilation error would be caught at the step that
  introduced it rather than requiring a bisection later.
- **Unit tests were actually executed**, not just written: `DependencyGraphTest`, `StageExecutorTest`,
  `PolicyGuardrailTest`, and `RateLimiterServiceTest` all pass (13/13). `StageExecutorTest` in particular
  exercises the real retry-then-rollback code path with a handler that fails twice and succeeds on the
  third attempt, and asserts the exact event sequence published.
- **Integration tests were written but not executed in this environment**, because the sandbox this was
  built in has no Docker daemon available (`docker` is not on `PATH`). They compile cleanly against the
  same Testcontainers APIs used elsewhere in the ecosystem, and are meant to be run via `./mvnw verify` on
  a machine with Docker -- see the README's test section. This is the single biggest verification gap in
  this deliverable and should be the first thing re-run in an environment with Docker available.
- **`docker-compose up` itself was not executed** for the same reason. The compose file's shape (healthcheck
  gating, port mappings, environment variable wiring matching `application.yml`'s placeholders) was
  reviewed carefully by hand, but has not been run end-to-end.
- **Scenario blocking behavior is a deliberate, documented trade-off.** `POST /scenarios/{type}/run` blocks
  the HTTP request thread for the pipeline's full duration, including any time spent waiting on a manual
  gate approval. This makes the manual-approval demo flow simple to drive with two curl calls in two
  terminals (see README), at the cost of not being how you'd want this endpoint to behave in a real
  production system fronting many concurrent users (there, you'd want `202 Accepted` + polling, or a
  webhook). Given the assessment's framing as a demonstration of the orchestration model rather than a
  production API, the simpler synchronous design was chosen deliberately.
- **`GateKeeper` resolves an approval by stage only, not by run ID**, because the REST contract specified in
  the brief (`POST /orchestration/gates/{stageId}/approve`) has no run ID in the path. It resolves whichever
  run currently has a pending gate for that stage. This is correct and sufficient for the intended one-demo-
  run-at-a-time usage pattern, and is called out explicitly in `GateKeeper`'s Javadoc as a scoped
  assumption, not a hidden limitation.

## Assumptions and limitations

- **No authentication/authorization layer.** `createdBy` on link creation and `approver` on gate decisions
  are free-text fields, not verified identities. Rate limiting is per-IP rather than per-user because there
  is no user identity to key on (see ADR-002).
- **No GeoIP lookup.** `click_events.country_code` is a real column but is always persisted as `null` in
  this build -- there's no GeoIP database or service wired in. Faking a plausible-looking country code
  would have been worse than being explicit that this field is unpopulated.
- **PolicyGuardrail and UrlValidator both implement SSRF/host-blocklist checks, deliberately duplicated
  rather than shared.** `PolicyGuardrail` (design-time governance, scanning requirement text during a
  pipeline run) and `UrlValidator` (runtime request validation, on the actual `POST /api/v1/links` path)
  serve different call sites with different lifecycles. Sharing one utility class between them would have
  meant either `orchestration/` depending on `service/` or vice versa, breaking the self-containment goal
  described above. The duplication is small (a host-prefix-match check) and each copy is independently unit
  tested.
- **Redis/Kafka failures are not explicitly circuit-broken.** If Redis is down, a cache-aside read/write
  will throw rather than transparently falling back to Postgres-only mode; the same applies to a Kafka
  publish failing on the redirect path. Adding explicit fallback/circuit-breaker behavior (e.g. Resilience4j)
  was judged out of scope for the assessment's time budget, and is the most realistic "if I had one more day"
  addition.
- **Migration numbers in the Brownfield scenario are simulated, not real.** The "V5 collision" the
  brownfield scenario's IMPLEMENTATION handler throws on its first attempt is a narrative device (a thrown
  exception with a descriptive message) rather than an actual second migration file colliding on disk --
  the point being demonstrated is the engine's retry/rollback/recovery mechanics, which is real code, not
  the specific mechanics of Flyway version negotiation.
- **Short codes are randomly generated (base62, 8 characters) with a bounded retry-on-collision loop**, not
  derived from a counter or hash of the URL. At 62^8 possible codes, collision probability is negligible at
  any realistic scale for this assessment; a production system serving billions of links might reconsider.

## Orchestration design decisions

The most consequential orchestration-specific decisions, and why, are detailed alongside the code and in
`docs/architecture-overview.md`. In summary:

- **Stage work is pluggable via `StageHandler`**, keeping the engine's coordination logic (`DependencyGraph`,
  `StageExecutor`, `GateKeeper`, `ContextStore`, `ObservabilityCollector`, `OrchestrationEngine`) entirely
  independent of what any given stage actually does.
- **Checkpoint/rollback is one mechanism reused between every retry attempt**, not a special case reserved
  for final failure -- see `StageExecutor.execute`.
- **Dynamic re-planning is an emergent property of the main loop re-evaluating executable stages every
  iteration**, driven by `PipelineContext.invalidateDownstream` and a post-stage-completion hook mechanism,
  rather than a bolted-on special code path for "handle architecture revisions."
- **Human gates block on a real `CompletableFuture`**, resolved by a separate HTTP call, so the manual-
  approval mode is genuinely demonstrable rather than simulated with a sleep-and-poll loop.
