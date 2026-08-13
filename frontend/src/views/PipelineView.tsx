import { PipelineGraph } from "../components/PipelineGraph/PipelineGraph";
import { ErrorBanner } from "../components/shared/ErrorBanner";
import { LoadingSkeleton } from "../components/shared/LoadingSkeleton";
import type { PipelineStatus } from "../types";
import styles from "./PipelineView.module.css";

interface PipelineViewProps {
  status: PipelineStatus | null;
  error: string | null;
  isPolling: boolean;
  onRefresh: () => void;
}

const OVERALL_CLASS: Record<string, string> = {
  COMPLETED: styles.overallCompleted,
  HALTED: styles.overallHalted,
  RUNNING: styles.overallRunning,
};

export function PipelineView({ status, error, isPolling, onRefresh }: PipelineViewProps) {
  if (!status) {
    if (error) {
      return (
        <div className={styles.view}>
          <div className={styles.empty}>
            <p>No pipeline run is available yet. Start one from the Scenario Runner, or retry if the backend just restarted.</p>
            <button onClick={onRefresh}>Retry</button>
          </div>
        </div>
      );
    }
    return (
      <div className={styles.view}>
        <LoadingSkeleton height="64px" />
        <div className={styles.skeletonGrid}>
          <LoadingSkeleton height="88px" />
          <LoadingSkeleton height="88px" />
          <LoadingSkeleton height="88px" />
        </div>
      </div>
    );
  }

  return (
    <div className={styles.view}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <h1 className={styles.title}>Pipeline Monitor</h1>
          <span className={styles.requirement}>
            {status.scenarioType} &middot; &ldquo;{status.requirement}&rdquo;
          </span>
        </div>
        <div className={styles.headerRight}>
          {isPolling && (
            <span className={styles.live}>
              <span className={styles.liveDot} aria-hidden="true" />
              live
            </span>
          )}
          <span className={`${styles.overall} ${OVERALL_CLASS[status.overallStatus] ?? ""}`}>{status.overallStatus}</span>
        </div>
      </div>

      {error && <ErrorBanner message={error} onRetry={onRefresh} />}

      <PipelineGraph status={status} onGateResolved={onRefresh} />
    </div>
  );
}
