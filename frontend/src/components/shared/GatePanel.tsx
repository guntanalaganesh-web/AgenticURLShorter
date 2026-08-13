import { useState } from "react";
import { resolveGate } from "../../api/api";
import type { Stage } from "../../types";
import styles from "./GatePanel.module.css";

interface GatePanelProps {
  stage: Extract<Stage, "ARCHITECTURE" | "RELEASE_READINESS">;
  onResolved: () => void;
}

const GATE_COPY: Record<GatePanelProps["stage"], string> = {
  ARCHITECTURE: "Design approval for the proposed architecture, tech stack, and ADR references before implementation begins.",
  RELEASE_READINESS: "Deploy approval for this run's release checklist -- coverage, OpenAPI spec, and migration review all already gate-checked by the engine.",
};

type Action = "approve" | "reject";
type PanelState = { kind: "idle" } | { kind: "armed"; action: Action } | { kind: "submitting" } | { kind: "error"; message: string };

/**
 * Shown inline on a gated stage node. The backend has no distinct
 * GATE_REQUIRED status -- GateKeeper blocks the stage's thread mid-RUNNING
 * while awaiting approval, so this panel appears whenever a gated stage
 * (ARCHITECTURE / RELEASE_READINESS) is RUNNING. Both actions post to the
 * same real endpoint (POST .../approve with {approved: true|false}); there
 * is no separate /reject route on the server.
 */
export function GatePanel({ stage, onResolved }: GatePanelProps) {
  const [state, setState] = useState<PanelState>({ kind: "idle" });

  function arm(action: Action) {
    setState({ kind: "armed", action });
  }

  async function confirm(action: Action) {
    setState({ kind: "submitting" });
    const result = await resolveGate(stage, action === "approve", "dashboard-operator", `${action}d via dashboard`);
    if (!result.ok) {
      setState({ kind: "error", message: result.message });
      return;
    }
    onResolved();
    setState({ kind: "idle" });
  }

  return (
    <div className={styles.panel}>
      <div className={styles.heading}>Gate awaiting approval</div>
      <p className={styles.description}>{GATE_COPY[stage]}</p>

      {state.kind === "idle" && (
        <div className={styles.actions}>
          <button className={`${styles.button} ${styles.approve}`} onClick={() => arm("approve")}>
            Approve
          </button>
          <button className={`${styles.button} ${styles.reject}`} onClick={() => arm("reject")}>
            Reject
          </button>
        </div>
      )}

      {state.kind === "armed" && (
        <div className={styles.actions}>
          <button
            className={`${styles.button} ${styles[state.action === "approve" ? "approve" : "reject"]} ${styles.armed}`}
            onClick={() => confirm(state.action)}
          >
            Confirm {state.action}?
          </button>
          <button className={`${styles.button} ${styles.cancel}`} onClick={() => setState({ kind: "idle" })}>
            Cancel
          </button>
        </div>
      )}

      {state.kind === "submitting" && (
        <div className={styles.actions}>
          <button className={styles.button} disabled>
            Submitting&hellip;
          </button>
        </div>
      )}

      {state.kind === "error" && (
        <>
          <p className={styles.error}>{state.message}</p>
          <div className={styles.actions}>
            <button className={`${styles.button} ${styles.cancel}`} onClick={() => setState({ kind: "idle" })}>
              Retry
            </button>
          </div>
        </>
      )}
    </div>
  );
}
