import type { StageState } from "../../types";
import styles from "./StatusBadge.module.css";

interface StatusBadgeProps {
  status: StageState;
  /** Optional override, e.g. "WAITING ON GATE" while still technically RUNNING. */
  label?: string;
}

const LABELS: Record<StageState, string> = {
  PENDING: "Pending",
  RUNNING: "Running",
  COMPLETED: "Completed",
  FAILED: "Failed",
  BLOCKED: "Blocked",
  SKIPPED: "Skipped",
};

/**
 * Encodes stage status through shape + motion + color together, never
 * color alone (WCAG color-not-only, and the brief's own "FAILED must feel
 * different in shape and texture" requirement). Each status below has a
 * genuinely distinct glyph -- hollow ring, pulsing dot, check, X, hexagon,
 * dashed ring -- so the badge reads correctly even in grayscale.
 */
export function StatusBadge({ status, label }: StatusBadgeProps) {
  const cls = styles[status.toLowerCase() as Lowercase<StageState>];

  return (
    <span className={`${styles.badge} ${cls}`}>
      <span className={styles.icon}>
        <StatusGlyph status={status} />
        {status === "RUNNING" && <span className={styles.pulseRing} aria-hidden="true" />}
      </span>
      <span className={styles.label}>{label ?? LABELS[status]}</span>
    </span>
  );
}

function StatusGlyph({ status }: { status: StageState }) {
  switch (status) {
    case "PENDING":
      return (
        <svg viewBox="0 0 14 14" aria-hidden="true">
          <circle cx="7" cy="7" r="5" />
        </svg>
      );
    case "SKIPPED":
      return (
        <svg viewBox="0 0 14 14" aria-hidden="true">
          <circle cx="7" cy="7" r="5" />
        </svg>
      );
    case "RUNNING":
      return (
        <svg viewBox="0 0 14 14" aria-hidden="true">
          <circle className="core" cx="7" cy="7" r="4" />
        </svg>
      );
    case "COMPLETED":
      return (
        <svg viewBox="0 0 14 14" aria-hidden="true">
          <circle cx="7" cy="7" r="6" />
          <path d="M4.3 7.2 L6.2 9 L9.8 5" />
        </svg>
      );
    case "FAILED":
      return (
        <svg viewBox="0 0 14 14" aria-hidden="true">
          <circle cx="7" cy="7" r="6" />
          <path d="M4.7 4.7 L9.3 9.3 M9.3 4.7 L4.7 9.3" />
        </svg>
      );
    case "BLOCKED":
      return (
        <svg viewBox="0 0 14 14" aria-hidden="true">
          <polygon points="7,0.5 12.5,3.75 12.5,10.25 7,13.5 1.5,10.25 1.5,3.75" />
          <rect x="4.5" y="6.2" width="5" height="1.6" rx="0.4" />
        </svg>
      );
  }
}
