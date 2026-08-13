import type { RunLogEntry } from "../../types";
import { formatDateTime, formatDuration } from "../../utils/format";
import styles from "./RunHistoryList.module.css";

interface RunHistoryListProps {
  runs: RunLogEntry[];
  onSelectRun: (runId: string) => void;
}

/** Last 5 runs, most recent first -- clicking one loads its decisions in the Decision Log view. */
export function RunHistoryList({ runs, onSelectRun }: RunHistoryListProps) {
  const recent = runs.slice(0, 5);

  return (
    <section className={styles.section}>
      <h2 className={styles.heading}>Run History</h2>
      {recent.length === 0 ? (
        <p className={styles.empty}>No runs recorded yet.</p>
      ) : (
        <div className={styles.table}>
          {recent.map((run) => {
            const duration = run.completedAt
              ? formatDuration(new Date(run.completedAt).getTime() - new Date(run.startedAt).getTime())
              : "—";
            return (
              <button key={run.runId} className={styles.row} onClick={() => onSelectRun(run.runId)}>
                <span className={styles.scenarioType}>{run.scenarioType}</span>
                <span className={styles.runId}>{run.runId}</span>
                <span className={styles.duration}>{duration}</span>
                <span className={`${styles.outcome} ${run.success ? styles.outcomeSuccess : styles.outcomeFailure}`}>
                  {run.success === null ? "unknown" : run.success ? "success" : "failure"}
                </span>
                <span className={styles.startedAt}>{formatDateTime(run.startedAt)}</span>
              </button>
            );
          })}
        </div>
      )}
    </section>
  );
}
