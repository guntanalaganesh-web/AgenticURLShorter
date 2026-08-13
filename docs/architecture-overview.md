# Architecture Overview

## Component diagram

```
                              HTTP clients (curl / Swagger UI)
                                          |
                                          v
                    +---------------------------------------------+
                    |              REST API layer (api/)          |
                    |  UrlController        OrchestrationController|
                    |  ScenarioController   GlobalExceptionHandler |
                    +---------------------------------------------+
                          |                          |
                          v                          v
         +--------------------------+   +-------------------------------+
         |  URL shortener domain    |   |    Orchestration engine        |
         |  (service/)              |   |    (orchestration/)            |
         |                          |   |                                 |
         |  UrlService              |   |  OrchestrationEngine (coord.)  |
         |  AnalyticsService        |   |   |-- DependencyGraph          |
         |  RateLimiterService      |   |   |-- StageExecutor            |
         |  KafkaPublisher          |   |   |-- GateKeeper               |
         |  UrlValidator            |   |   |-- PolicyGuardrail          |
         |                          |   |   |-- ContextStore             |
         |                          |   |   +-- ObservabilityCollector   |
         +--------------------------+   +-------------------------------+
                |         |    |                       |
                v         v    v                       v
          +---------+ +-------+ +-------+      +----------------+
          | Postgres| | Redis | | Kafka |      |  scenario/     |
          | 15      | | 7     | | 3.6   |      |  GreenField    |
          | (JPA +  | |(cache-| |(async |      |  BrownField    |
          | Flyway) | | aside,| | click |      |  Ambiguous     |
          |         | | rate  | | events|      |  (register     |
          |         | | limit)| |)      |      |  StageHandlers |
          +---------+ +-------+ +-------+      |  on a          |
                                                |  PipelineContext)|
                                                +----------------+
```

Two independent subsystems share one codebase: the **orchestration engine** (generic, self-contained,
knows nothing about URL shorteners) and the **URL shortener service** (the thing being built). The
**scenario** package is what wires them together -- each scenario supplies `StageHandler` implementations
that do real work (or realistic simulated work) for each of the seven SDLC stages, and hands them to the
engine via a `PipelineContext`.

## Orchestration model

**Stages and dependencies.** `DependencyGraph` models seven stages as a DAG:

```
REQUIREMENTS -> ARCHITECTURE -> TASK_PLANNING -> { IMPLEMENTATION, TESTING, DOCUMENTATION } -> RELEASE_READINESS
```

The three middle stages share one dependency (TASK_PLANNING) and nothing depends on any one of them
individually until RELEASE_READINESS, so `getExecutableStages()` naturally returns all three together once
TASK_PLANNING completes -- the engine runs them concurrently via `CompletableFuture`, with no special-case
branching for "these three are the parallel ones."

**Extensibility over the graph is generic**, not hardcoded to this shape: `DependencyGraph` is a plain
`addDependency(stage, dependsOn)` builder with Kahn's-algorithm topological sort and cycle detection, so
`DependencyGraphTest` can construct and validate an intentionally cyclic graph without touching the
"real" SDLC wiring.

**Stage work is pluggable.** `StageHandler` is a single-method functional interface:
`StageExecutionResult execute(PipelineContext context, Stage stage, int attempt)`. The engine and
`StageExecutor` know nothing about what a stage *does* -- only that it returns an artifact and whether
exit criteria were met. This is what keeps `orchestration/` self-contained: it has zero compile-time
dependency on `scenario/` or `service/`. The dependency points the other way -- `scenario/` depends on
`orchestration/`.

**Policy checks need to see artifacts without coupling to their types.** `PolicyGuardrail` (in
`orchestration/`) has to inspect a TASK_PLANNING artifact for hardcoded secrets, or a TESTING artifact's
coverage percentage -- but those concrete types (`TaskPlan`, `TestReport`, ...) live in `scenario/`.
The fix is four small marker interfaces defined in `orchestration.model`
(`PolicyScannable`, `CoverageReporting`, `OpenApiPublished`, `MigrationAudit`) that scenario artifacts
implement. `orchestration/` depends only on the interfaces it defines; `scenario/` fulfills them. This is
plain dependency inversion, and it's why the marker interfaces exist instead of `PolicyGuardrail` doing
`instanceof TaskPlan` checks.

**Retry, checkpoint, and rollback are one mechanism, not three.** `StageExecutor` retries a stage up to
`orchestration.retry.max-attempts` times with exponential backoff. Between every failed attempt --
including the final one -- it restores `PipelineContext` to the most recent `Checkpoint`
(a snapshot of every stage's state and artifact, captured by the engine immediately after each stage
completes). This means a mid-attempt failure never leaves stray partial state behind for the next attempt,
and "rollback to last stable checkpoint" isn't a special final-failure-only code path -- it's the same
restore operation used between every retry. See the Brownfield scenario for this running live: its
IMPLEMENTATION handler deliberately fails on attempt 1 (a simulated Flyway migration version collision),
which triggers a real checkpoint restore and `RollbackEvent`, then succeeds on attempt 2 with a corrected
migration version.

**Dynamic re-planning falls out of the same event loop, with no special-casing.** `OrchestrationEngine`'s
main loop is: ask the graph which stages are executable, run that batch, repeat until terminal. A stage
becomes executable again if it's back in `PENDING` with its dependencies `COMPLETED` -- that's it, no
extra branch needed. `PipelineContext.registerPostStageHook(stage, hook)` lets a scenario register work to
run once, right after a stage completes; `OrchestrationEngine.reviseStageOutput(...)` (called from such a
hook) updates an upstream artifact and calls `PipelineContext.invalidateDownstream(...)`, which resets any
already-`COMPLETED` stage that transitively depends on the revised one back to `PENDING`. The next loop
iteration picks it back up automatically. The Ambiguous scenario uses exactly this: a hook on
TASK_PLANNING's first completion revises ARCHITECTURE's rate-limit value from 100 to 60 req/min, which
invalidates TASK_PLANNING, which the engine re-executes against the corrected architecture before ever
touching IMPLEMENTATION.

**Human gates are a blocking future, not a poll loop.** `GateKeeper.waitForApproval` either resolves
immediately (`auto-approve=true`) or blocks the calling stage's thread on a `CompletableFuture` that
`POST /orchestration/gates/{stageId}/approve` completes from a separate HTTP request. This means the demo
can genuinely exercise the manual-approval path with two curl calls in two terminals, not a status flag
you have to trust.

## Control flow walkthrough (one stage, end to end)

1. `OrchestrationEngine.runPipeline` asks `DependencyGraph.getExecutableStages` for the next batch and
   dispatches each stage to `runStage` on a small thread pool.
2. `runStage` marks the stage `RUNNING`, then calls `PolicyGuardrail.validate(stage, context)`. A
   violation halts the pipeline immediately with a `SafeStopEvent` -- no retry, no partial execution.
3. If the stage is gated (ARCHITECTURE or RELEASE_READINESS), `GateKeeper.waitForApproval` blocks until
   resolved. A rejection halts the pipeline the same way a policy violation does.
4. `StageExecutor.execute` runs the registered `StageHandler`, up to `max-attempts` times, publishing
   `StageCompletedEvent` or `StageFailedEvent` on every attempt and restoring the last checkpoint between
   failures.
5. On success, the engine marks the stage `COMPLETED`, `ContextStore.saveStageOutput` persists the
   artifact to `orchestration_context` (Postgres, JSONB), a fresh `Checkpoint` is captured, and any
   post-completion hooks registered for that stage run.
6. `ObservabilityCollector` listens to every event above (`@EventListener`) and updates Micrometer
   counters/timers in real time; it writes one summary row to `orchestration_run_log` when the whole
   pipeline concludes.
7. The loop repeats until every stage is `COMPLETED`/`SKIPPED` (success) or the context is `halted`
   (policy violation, gate rejection, or exhausted retries).

## Key design decisions

- **Self-contained orchestration package.** `orchestration/` has no compile-time dependency on `scenario/`
  or `service/`; extension points are interfaces (`StageHandler`, `PolicyScannable`, `CoverageReporting`,
  `OpenApiPublished`, `MigrationAudit`) it defines and others implement. This is what makes the engine
  independently testable (see `DependencyGraphTest`, `StageExecutorTest`, `PolicyGuardrailTest` -- none of
  them touch the URL shortener) and, in principle, reusable for a pipeline that builds something other
  than a URL shortener.
- **Records over mutable DTOs wherever state doesn't need to change.** Every artifact, event payload, and
  API request/response is a `record`; the few genuinely mutable things (`PipelineContext`, `ShortLink`'s
  `active` flag) are mutable on purpose and documented as such.
- **No Lombok.** Records eliminate most of the boilerplate Lombok would have removed; the remaining
  hand-written getters are few enough that a code-generation dependency wasn't worth adding.
- **Cache-aside over write-through for Redis** and **sliding-window over fixed-window for rate
  limiting** are both large enough decisions to warrant their own ADRs -- see
  [ADR-001](ADR-001-caching-strategy.md) and [ADR-002](ADR-002-rate-limiting-algorithm.md).
- **Kafka decouples click recording from the redirect path** -- see
  [ADR-003](ADR-003-analytics-async.md).
