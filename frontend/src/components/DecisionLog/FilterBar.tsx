import { ALL_STAGES } from "../../types";
import type { RunLogEntry, Stage } from "../../types";
import { formatDateTime } from "../../utils/format";
import styles from "./FilterBar.module.css";

interface FilterBarProps {
  stageFilter: Set<Stage>;
  onToggleStage: (stage: Stage) => void;
  runs: RunLogEntry[];
  runFilter: string | null;
  onChangeRunFilter: (runId: string | null) => void;
  search: string;
  onChangeSearch: (value: string) => void;
  resultCount: number;
}

export function FilterBar({
  stageFilter,
  onToggleStage,
  runs,
  runFilter,
  onChangeRunFilter,
  search,
  onChangeSearch,
  resultCount,
}: FilterBarProps) {
  return (
    <div className={styles.bar}>
      <div className={styles.group}>
        <span className={styles.groupLabel}>Stage</span>
        <div className={styles.chips}>
          {ALL_STAGES.map((stage) => (
            <button
              key={stage}
              className={`${styles.chip} ${stageFilter.has(stage) ? styles.chipActive : ""}`}
              onClick={() => onToggleStage(stage)}
              aria-pressed={stageFilter.has(stage)}
            >
              {stage.replace("_", " ")}
            </button>
          ))}
        </div>
      </div>

      <div className={styles.group}>
        <span className={styles.groupLabel}>Run</span>
        <select
          className={styles.select}
          value={runFilter ?? ""}
          onChange={(e) => onChangeRunFilter(e.target.value || null)}
        >
          <option value="">All runs</option>
          {runs.map((run) => (
            <option key={run.runId} value={run.runId}>
              {run.scenarioType} &middot; {formatDateTime(run.startedAt)}
            </option>
          ))}
        </select>
      </div>

      <input
        className={styles.search}
        type="search"
        placeholder="Search decisions…"
        value={search}
        onChange={(e) => onChangeSearch(e.target.value)}
        aria-label="Search decision log"
      />

      <span className={styles.count} aria-live="polite">
        {resultCount} {resultCount === 1 ? "entry" : "entries"}
      </span>
    </div>
  );
}
