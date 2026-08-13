/**
 * All types mirror the REAL Spring Boot API contract exactly (verified live
 * against the running backend, not the illustrative names in the design
 * brief). Two deliberate reconciliations against the brief, both documented
 * again in main.tsx's final quality-check block:
 *
 *  - Stage status is PENDING | BLOCKED | RUNNING | COMPLETED | FAILED | SKIPPED
 *    (the engine's real StageState enum) rather than an invented IN_PROGRESS.
 *  - There is no server-side GATE_REQUIRED status and no /reject endpoint.
 *    Gates are orthogonal to stage state: GateKeeper blocks the stage thread
 *    mid-RUNNING while awaiting approval. The dashboard treats "a gated
 *    stage sitting in RUNNING" as "possibly awaiting a gate" and offers the
 *    approval panel there; rejection posts {approved:false} to the same
 *    /approve endpoint, since that's the only one the server exposes.
 */

export type Stage =
  | "REQUIREMENTS"
  | "ARCHITECTURE"
  | "TASK_PLANNING"
  | "IMPLEMENTATION"
  | "TESTING"
  | "DOCUMENTATION"
  | "RELEASE_READINESS";

export type StageState = "PENDING" | "BLOCKED" | "RUNNING" | "COMPLETED" | "FAILED" | "SKIPPED";

export type OverallStatus = "RUNNING" | "COMPLETED" | "HALTED";

export type Confidence = "HIGH" | "MEDIUM" | "LOW";

export type ScenarioType = "greenfield" | "brownfield" | "ambiguous";

/** The two stages GateKeeper enforces human approval on. */
export const GATED_STAGES: ReadonlySet<Stage> = new Set(["ARCHITECTURE", "RELEASE_READINESS"]);

/** Fixed pipeline topology -- mirrors DependencyGraph.standardSdlcGraph() exactly. */
export const ALL_STAGES: readonly Stage[] = [
  "REQUIREMENTS",
  "ARCHITECTURE",
  "TASK_PLANNING",
  "IMPLEMENTATION",
  "TESTING",
  "DOCUMENTATION",
  "RELEASE_READINESS",
];

export interface StageTiming {
  attemptCount: number;
  startedAt: string;
  endedAt: string;
  durationMillis: number;
}

export interface PipelineStatus {
  runId: string;
  scenarioType: string;
  requirement: string;
  overallStatus: OverallStatus;
  stageStates: Partial<Record<Stage, StageState>>;
  stageTimings: Partial<Record<Stage, StageTiming>>;
  halted: boolean;
  haltReason: string | null;
  startedAt: string | null;
  endedAt: string | null;
}

export interface DecisionRecord {
  id: string;
  runId: string;
  stage: Stage;
  decision: string;
  rationale: string;
  alternativesConsidered: string[];
  timestamp: string;
}

export interface AmbiguityRecord {
  id: string;
  runId: string;
  stage: Stage;
  question: string;
  assumption: string;
  confidence: Confidence;
  impactIfWrong: string;
  timestamp: string;
}

export interface DecisionLogResponse {
  decisions: DecisionRecord[];
  ambiguities: AmbiguityRecord[];
}

export interface RunLogEntry {
  runId: string;
  scenarioType: string;
  startedAt: string;
  completedAt: string | null;
  success: boolean | null;
}

export interface GateResolutionResponse {
  runId: string;
  stage: Stage;
  approved: boolean;
  resolved: boolean;
}

export interface ApiError {
  code: string;
  message: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ApiError | null;
  timestamp: string;
}

export const SCENARIOS: ReadonlyArray<{
  type: ScenarioType;
  name: string;
  description: string;
  requirement: string;
}> = [
  {
    type: "greenfield",
    name: "Greenfield",
    description: "Fresh pipeline run through all seven stages, both approval gates, every stage producing a real artifact.",
    requirement: "Build URL shortener from scratch",
  },
  {
    type: "brownfield",
    name: "Brownfield",
    description: "Impact analysis against the existing codebase, then a deliberate migration collision to exercise retry, rollback, and recovery.",
    requirement: "Add Redis caching and Kafka analytics to the existing service",
  },
  {
    type: "ambiguous",
    name: "Ambiguous",
    description: "Surfaces and resolves three requirement ambiguities, then dynamically re-plans mid-run when the architecture is revised.",
    requirement: "Add rate limiting",
  },
];
