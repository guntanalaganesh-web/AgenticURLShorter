import { useEffect, useState } from "react";
import { GatePanel } from "../shared/GatePanel";
import { StatusBadge } from "../shared/StatusBadge";
import { GATED_STAGES } from "../../types";
import type { Stage, StageState, StageTiming } from "../../types";

function isGatedStage(stage: Stage): stage is "ARCHITECTURE" | "RELEASE_READINESS" {
  return GATED_STAGES.has(stage);
}
import { formatDuration, stageLabel } from "../../utils/format";
import styles from "./StageNode.module.css";

/** Matches orchestration.retry.max-attempts in application.yml. Not
 * exposed via any endpoint, so it's a documented static assumption rather
 * than fabricated per-run data (the attempt count itself IS real). */
const MAX_ATTEMPTS = 3;

interface StageNodeProps {
  stage: Stage;
  state: StageState;
  timing?: StageTiming;
  isHaltedHere: boolean;
  haltReason: string | null;
  onGateResolved: () => void;
}

export function StageNode({ stage, state, timing, isHaltedHere, haltReason, onGateResolved }: StageNodeProps) {
  const isGated = GATED_STAGES.has(stage);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (state !== "RUNNING") return;
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [state]);

  const durationMs = timing
    ? state === "RUNNING"
      ? now - new Date(timing.startedAt).getTime()
      : timing.durationMillis
    : null;

  // A gated stage sitting in RUNNING is, in practice, waiting on the human
  // gate (guardrail + the handler itself resolve in milliseconds) -- see
  // GatePanel's own header comment for the full reasoning.
  const showGatePanel = isGated && state === "RUNNING";

  return (
    <div className={`${styles.node} ${styles[state.toLowerCase()]}`}>
      <div className={styles.top}>
        <span className={styles.name}>
          {stageLabel(stage)}
          {isGated && (
            <span className={styles.gateMark} title="Requires human approval">
              <svg viewBox="0 0 12 12" fill="none" aria-hidden="true">
                <rect x="2.5" y="5.5" width="7" height="5" rx="0.8" stroke="currentColor" strokeWidth="1.2" />
                <path d="M4 5.5V4a2 2 0 0 1 4 0v1.5" stroke="currentColor" strokeWidth="1.2" fill="none" />
              </svg>
            </span>
          )}
        </span>
        <StatusBadge status={state} />
      </div>

      {(durationMs !== null || (timing && timing.attemptCount > 1)) && (
        <div className={styles.meta}>
          {durationMs !== null && <span className={styles.duration}>{formatDuration(durationMs)}</span>}
          {timing && timing.attemptCount > 1 && (
            <span className={styles.attempt}>
              attempt {timing.attemptCount}/{MAX_ATTEMPTS}
            </span>
          )}
        </div>
      )}

      {isHaltedHere && haltReason && <p className={styles.haltReason}>{haltReason}</p>}

      {showGatePanel && isGatedStage(stage) && <GatePanel stage={stage} onResolved={onGateResolved} />}
    </div>
  );
}
