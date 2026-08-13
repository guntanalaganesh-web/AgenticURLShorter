import type { RunLogEntry } from "../../types";
import { formatDuration, formatTime } from "../../utils/format";
import styles from "./ScenarioCard.module.css";

interface ScenarioCardProps {
  name: string;
  description: string;
  requirement: string;
  lastRun?: RunLogEntry;
  isRunning: boolean;
  onRun: () => void;
}

export function ScenarioCard({ name, description, requirement, lastRun, isRunning, onRun }: ScenarioCardProps) {
  return (
    <article className={styles.card}>
      <h2 className={styles.name}>{name}</h2>
      <p className={styles.description}>{description}</p>

      <div>
        <div className={styles.requirementLabel}>Requirement sent to engine</div>
        <div className={styles.requirement}>&ldquo;{requirement}&rdquo;</div>
      </div>

      <div className={styles.footer}>
        {lastRun ? (
          <div className={styles.lastRun}>
            <span>last run {formatTime(lastRun.startedAt)}</span>
            <span>
              <span className={`${styles.lastRunOutcome} ${lastRun.success ? styles.outcomeSuccess : styles.outcomeFailure}`}>
                {lastRun.success ? "success" : "failure"}
              </span>
              {lastRun.completedAt && (
                <> &middot; {formatDuration(new Date(lastRun.completedAt).getTime() - new Date(lastRun.startedAt).getTime())}</>
              )}
            </span>
          </div>
        ) : (
          <div className={styles.lastRun}>
            <span>no runs yet</span>
          </div>
        )}

        <button className={styles.runButton} onClick={onRun} disabled={isRunning} aria-label={`Run ${name} scenario`}>
          {isRunning ? "Running…" : "Run"}
        </button>
      </div>
    </article>
  );
}
