import { Fragment } from "react";
import { FanConnector } from "./FanConnector";
import { StageNode } from "./StageNode";
import styles from "./PipelineGraph.module.css";
import type { PipelineStatus, Stage } from "../../types";

const CHAIN_STAGES: Stage[] = ["REQUIREMENTS", "ARCHITECTURE", "TASK_PLANNING"];
const PARALLEL_STAGES: Stage[] = ["IMPLEMENTATION", "TESTING", "DOCUMENTATION"];
const FINAL_STAGE: Stage = "RELEASE_READINESS";

interface PipelineGraphProps {
  status: PipelineStatus;
  onGateResolved: () => void;
}

/**
 * Renders the SDLC pipeline as a dependency graph, not a linear stepper:
 * REQUIREMENTS -> ARCHITECTURE -> TASK_PLANNING feed a genuine three-way
 * fan-out (IMPLEMENTATION / TESTING / DOCUMENTATION run concurrently once
 * TASK_PLANNING completes) that converges on RELEASE_READINESS. The two
 * sections are laid out independently (a flex chain, then a 3-column
 * grid) rather than forced into one shared column grid, since they
 * represent different relationships -- sequence vs. concurrency -- and
 * conflating their column widths would imply an alignment that isn't
 * structurally true.
 */
export function PipelineGraph({ status, onGateResolved }: PipelineGraphProps) {
  const isPastTaskPlanning = status.stageStates.TASK_PLANNING !== "PENDING";

  return (
    <div className={styles.graph}>
      <div className={styles.chain}>
        {CHAIN_STAGES.map((stage, i) => (
          <Fragment key={stage}>
            <div className={styles.nodeWrap}>
              <StageNode
                stage={stage}
                state={status.stageStates[stage] ?? "PENDING"}
                timing={status.stageTimings[stage]}
                isHaltedHere={status.halted && status.stageStates[stage] === "FAILED"}
                haltReason={status.haltReason}
                onGateResolved={onGateResolved}
              />
            </div>
            {i < CHAIN_STAGES.length - 1 && <ChainArrow />}
          </Fragment>
        ))}
      </div>

      <div className={styles.connectorZone}>
        <FanConnector direction="out" active={isPastTaskPlanning} />
      </div>

      <div className={styles.parallelRow}>
        {PARALLEL_STAGES.map((stage) => (
          <StageNode
            key={stage}
            stage={stage}
            state={status.stageStates[stage] ?? "PENDING"}
            timing={status.stageTimings[stage]}
            isHaltedHere={status.halted && status.stageStates[stage] === "FAILED"}
            haltReason={status.haltReason}
            onGateResolved={onGateResolved}
          />
        ))}
      </div>

      <div className={styles.connectorZone}>
        <FanConnector
          direction="in"
          active={PARALLEL_STAGES.every((s) => status.stageStates[s] === "COMPLETED" || status.stageStates[s] === "SKIPPED")}
        />
      </div>

      <div className={styles.finalRow}>
        <StageNode
          stage={FINAL_STAGE}
          state={status.stageStates[FINAL_STAGE] ?? "PENDING"}
          timing={status.stageTimings[FINAL_STAGE]}
          isHaltedHere={status.halted && status.stageStates[FINAL_STAGE] === "FAILED"}
          haltReason={status.haltReason}
          onGateResolved={onGateResolved}
        />
      </div>
    </div>
  );
}

function ChainArrow() {
  return (
    <svg className={styles.arrow} viewBox="0 0 28 14" fill="none" aria-hidden="true">
      <line x1="0" y1="7" x2="21" y2="7" stroke="currentColor" strokeWidth="1.5" />
      <path d="M16 2 L22 7 L16 12" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
