# URL Shortener, Built and Managed by an Agentic SDLC Orchestration Engine

This repository is a technical assessment deliverable. The primary artifact under evaluation is the
**orchestration engine** at [`src/main/java/com/schwab/assessment/orchestration/`](src/main/java/com/schwab/assessment/orchestration/) --
a self-contained engine that drives a seven-stage SDLC pipeline (requirements through release readiness)
with dependency-aware parallel execution, retry/rollback, human approval gates, policy guardrails,
decision lineage, and reliability metrics. The **URL shortener service** it builds and operates on is the
substrate the engine is exercised against, in three scenarios: greenfield, brownfield, and an
intentionally ambiguous requirement.

See [`docs/architecture-overview.md`](docs/architecture-overview.md) for how the pieces fit together and
[`docs/engineering-summary.md`](docs/engineering-summary.md) for the full rationale, trade-offs, and
known limitations.

A visual dashboard for the engine -- a live dependency-graph view of the pipeline, a scenario runner,
and a decision-log browser -- lives in [`frontend/`](frontend/README.md). It's a separate concern from
the backend below and has its own setup instructions.

## Live demo

Both halves are deployed and talking to each other right now, no local setup required:

- **Dashboard:** https://frontend-nu-steel-80.vercel.app (Vercel)
- **Backend API:** https://schwab-orchestration.onrender.com (Render -- Spring Boot + managed Postgres +
  managed Redis)

Open the dashboard and hit "Run" on any of the three scenario cards to watch a real pipeline execute
against the live backend.

> **Heads up:** the backend's Postgres and Redis are on Render's free tier, provisioned for this
> assessment, and **expire around 2026-09-12**. After that the demo link will show "backend unreachable"
> until the databases are recreated or upgraded to a paid plan. Kafka/ZooKeeper are intentionally not
> deployed here -- the dashboard never exercises that code path, and Spring Kafka doesn't fail startup
> when the broker is unreachable, it just retries in the background (see
> [`docs/engineering-summary.md`](docs/engineering-summary.md)). Local `docker-compose up` below still runs
> the full stack, Kafka included.

## Prerequisites

- Docker and Docker Compose
- JDK 17 (only needed if you want to run Maven directly instead of via Docker)
- Node 18+ (only needed for the dashboard in `frontend/`)
- `curl` (or any HTTP client) for the commands below

No local Maven install is required -- this repo vendors the Maven Wrapper (`mvnw` / `mvnw.cmd`), which
downloads Maven itself on first use.

## Running the full stack

```bash
docker-compose up --build
```

This starts PostgreSQL 15, Redis 7, ZooKeeper, Kafka 3.6, and the Spring Boot app, in that dependency
order -- the app container's healthcheck-gated `depends_on` means it won't start until Postgres and Redis
report healthy. Flyway migrates the schema automatically on app startup. Once healthy:

- App: http://localhost:8081
- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI spec: http://localhost:8081/v3/api-docs
- Prometheus-compatible metrics: http://localhost:8081/actuator/metrics (and `/actuator/prometheus`)

To run against local infra instead of full docker-compose (e.g. while iterating on the app), start just
the infra services and run the app with Maven:

```bash
docker-compose up postgres redis zookeeper kafka
./mvnw spring-boot:run
```

## Running tests

```bash
# Unit tests only (fast, no Docker required)
./mvnw test -Dtest='!*IntegrationTest'

# Full suite, including Testcontainers integration tests (requires Docker)
./mvnw verify
```

The integration tests (`UrlShortenerIntegrationTest`, `OrchestrationIntegrationTest`) spin up real
PostgreSQL, Redis, and Kafka containers via Testcontainers -- nothing in those tests is mocked.

## Triggering the three demonstration scenarios

Each scenario runs a full pipeline and blocks until it reaches a terminal state. In the default demo
config (`orchestration.gates.auto-approve=true`), the ARCHITECTURE and RELEASE_READINESS gates
self-approve immediately, so each call returns as soon as the pipeline finishes.

```bash
# Greenfield: "Build URL shortener from scratch" -- all 7 stages, both gates, every stage
# produces a real artifact (RequirementsDocument, ArchitectureDocument, TaskPlan, ...).
curl -s -X POST http://localhost:8081/scenarios/greenfield/run | jq

# Brownfield: "Add Redis caching and Kafka analytics to the existing service" -- runs an
# impact-analysis pass first, then injects a deliberate Flyway migration version collision
# into IMPLEMENTATION so you can watch StageExecutor retry, roll back to the last checkpoint,
# and recover with a corrected migration version.
curl -s -X POST http://localhost:8081/scenarios/brownfield/run | jq

# Ambiguous: "Add rate limiting" -- REQUIREMENTS surfaces and resolves 3 ambiguities (scope,
# algorithm, limit) with confidence + impact-if-wrong. Partway through, a simulated
# capacity-planning update revises the rate limit from 100 to 60 req/min; watch
# TASK_PLANNING get invalidated and dynamically re-planned against the new value.
curl -s -X POST http://localhost:8081/scenarios/ambiguous/run | jq
```

## Viewing pipeline status and the decision log

```bash
# Current (most recently started) pipeline's stage-by-stage status
curl -s http://localhost:8081/orchestration/status | jq

# A specific run
curl -s "http://localhost:8081/orchestration/status?runId=<uuid-from-a-scenario-response>" | jq

# Full decision log (all runs) plus ambiguity resolutions for the current run
curl -s http://localhost:8081/orchestration/decisions | jq
```

## Testing manual gate approval (`auto-approve=false`)

By default the demo auto-approves gates so a scenario call returns immediately. To see the actual human
approval checkpoint in action:

```bash
# 1. Start the stack with auto-approve disabled
ORCHESTRATION_GATES_AUTO_APPROVE=false docker-compose up --build
```

Then, in one terminal, kick off a scenario -- this call will **block** at the ARCHITECTURE gate:

```bash
curl -s -X POST http://localhost:8081/scenarios/greenfield/run | jq
```

In a second terminal, confirm the pipeline is waiting on the gate:

```bash
curl -s http://localhost:8081/orchestration/status | jq
# stageStates.ARCHITECTURE will show RUNNING; the gate itself is PENDING
```

Approve it (or reject it, to see the halt-and-rollback path):

```bash
# Approve
curl -s -X POST http://localhost:8081/orchestration/gates/ARCHITECTURE/approve \
  -H 'Content-Type: application/json' \
  -d '{"approved": true, "approver": "jane.doe", "reason": "design looks good"}' | jq

# ...or reject
curl -s -X POST http://localhost:8081/orchestration/gates/ARCHITECTURE/approve \
  -H 'Content-Type: application/json' \
  -d '{"approved": false, "approver": "jane.doe", "reason": "needs a caching layer diagram first"}' | jq
```

The blocked call in the first terminal will then return -- either continuing on to TASK_PLANNING, or
halted with a `SafeStop` if you rejected it. The same flow applies to the RELEASE_READINESS gate later in
the run.

## URL shortener API

```bash
# Create a short link
curl -s -X POST http://localhost:8081/api/v1/links \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com/some/long/path", "createdBy": "demo-user"}' | jq

# Redirect (302 by default; 301 if shortlink.redirect.permanent=true)
curl -si http://localhost:8081/<code>

# Analytics
curl -s http://localhost:8081/api/v1/links/<code>/analytics | jq

# Soft-delete
curl -s -X DELETE http://localhost:8081/api/v1/links/<code> | jq
```

## Configuration reference

| Property | Default | Purpose |
|---|---|---|
| `orchestration.gates.auto-approve` | `true` | Self-approve ARCHITECTURE/RELEASE_READINESS gates for demos |
| `orchestration.retry.max-attempts` | `3` | Max attempts per stage before rollback + halt |
| `orchestration.retry.backoff-multiplier` | `2` | Exponential backoff multiplier between attempts |
| `rate-limiter.requests-per-minute` | `100` | Sliding-window budget per client IP |
| `shortlink.default-ttl-hours` | `8760` | Default link lifetime (1 year) when not specified at creation |
| `shortlink.redirect.permanent` | `false` | `301` vs `302` on redirect |

All of the above can be overridden via environment variables (see `application.yml` for the exact
mapping) or by editing `docker-compose.yml`.

## How the live demo is deployed

Documented here for reproducibility, not as setup instructions you need to follow for local dev.

**Backend -- Render** (`schwab-orchestration` web service, Oregon region, free tier):
- Built directly from this repo's root `Dockerfile` via Render's Docker runtime (auto-deploys on push to
  `main`).
- A managed Postgres (`schwab-postgres`) and managed Redis/Key-Value (`schwab-redis`) instance, both free
  tier, both **with an explicit `ipAllowList` of `0.0.0.0/0`** -- Render's databases default to a `null`
  allow list, which blocks all external connections (not documented anywhere obvious; the actual symptom
  is a bare `java.io.EOFException` mid-authentication, which looks like an SSL problem and isn't one).
- Redis requires ACL-style `username` + `password` + TLS for external connections, which
  `application.yml` didn't originally support -- see `spring.data.redis.username` /`.password` / `.ssl.enabled`,
  all opt-in via env vars and inert for local Docker Redis.
- `PORT` and `SERVER_PORT` are both pinned to `8080` as env vars so Render's port-detection and Spring
  Boot's actual bind port always agree, rather than relying on Render's port auto-detection picking up the
  Dockerfile's `EXPOSE 8080` on its own.
- No Kafka/ZooKeeper services are deployed (see the live-demo note above for why that's safe here).

**Frontend -- Vercel** (`frontend/` deployed as its own project, auto-detected as Vite):
- `VITE_API_BASE_URL` is set as a production environment variable to the Render backend's URL, baked into
  the static build at deploy time (Vite env vars are compile-time, not runtime).
- The backend's CORS config (`WebConfig` for regular endpoints, `management.endpoints.web.cors.*` in
  `application.yml` for Actuator specifically, since it's served by a separate handler mapping) allows both
  the Vercel production alias and every per-deployment preview URL for this project.
