import { useEffect, useMemo, useState } from "react";
import { getDecisions, getRuns } from "../../api/api";
import { ErrorBanner } from "../shared/ErrorBanner";
import { LoadingSkeleton } from "../shared/LoadingSkeleton";
import { FilterBar } from "./FilterBar";
import { DecisionEntry } from "./DecisionEntry";
import type { LogEntry } from "./DecisionEntry";
import type { RunLogEntry, Stage } from "../../types";
import styles from "./DecisionLog.module.css";

interface DecisionLogProps {
  selectedRunId: string | null;
}

export function DecisionLog({ selectedRunId }: DecisionLogProps) {
  const [entries, setEntries] = useState<LogEntry[] | null>(null);
  const [runs, setRuns] = useState<RunLogEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [stageFilter, setStageFilter] = useState<Set<Stage>>(new Set());
  const [runFilter, setRunFilter] = useState<string | null>(selectedRunId);
  const [search, setSearch] = useState("");

  useEffect(() => {
    setRunFilter(selectedRunId);
  }, [selectedRunId]);

  async function load() {
    setError(null);
    const [decisionsResult, runsResult] = await Promise.all([getDecisions(), getRuns()]);

    if (!decisionsResult.ok) {
      setError(decisionsResult.message);
      return;
    }
    if (runsResult.ok) setRuns(runsResult.data);

    const decisionEntries: LogEntry[] = decisionsResult.data.decisions.map((d) => ({
      id: d.id,
      runId: d.runId,
      stage: d.stage,
      timestamp: d.timestamp,
      headline: d.decision,
      body: d.rationale,
      alternatives: d.alternativesConsidered,
      kind: "decision",
    }));

    const ambiguityEntries: LogEntry[] = decisionsResult.data.ambiguities.map((a) => ({
      id: a.id,
      runId: a.runId,
      stage: a.stage,
      timestamp: a.timestamp,
      headline: a.question,
      body: `Assumption: ${a.assumption}. Impact if wrong: ${a.impactIfWrong}`,
      alternatives: [],
      kind: "ambiguity",
      confidence: a.confidence,
    }));

    const merged = [...decisionEntries, ...ambiguityEntries].sort(
      (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    );
    setEntries(merged);
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function toggleStage(stage: Stage) {
    setStageFilter((prev) => {
      const next = new Set(prev);
      if (next.has(stage)) next.delete(stage);
      else next.add(stage);
      return next;
    });
  }

  const filtered = useMemo(() => {
    if (!entries) return [];
    const query = search.trim().toLowerCase();
    return entries.filter((e) => {
      if (stageFilter.size > 0 && !stageFilter.has(e.stage)) return false;
      if (runFilter && e.runId !== runFilter) return false;
      if (query) {
        const haystack = `${e.headline} ${e.body} ${e.alternatives.join(" ")}`.toLowerCase();
        if (!haystack.includes(query)) return false;
      }
      return true;
    });
  }, [entries, stageFilter, runFilter, search]);

  if (!entries) {
    if (error) return <ErrorBanner message={error} onRetry={load} />;
    return (
      <div className={styles.view}>
        <LoadingSkeleton height="32px" />
        <LoadingSkeleton height="80px" />
        <LoadingSkeleton height="80px" />
        <LoadingSkeleton height="80px" />
      </div>
    );
  }

  return (
    <div className={styles.view}>
      <h1 className={styles.title}>Decision Log</h1>

      {error && <ErrorBanner message={error} onRetry={load} />}

      <FilterBar
        stageFilter={stageFilter}
        onToggleStage={toggleStage}
        runs={runs}
        runFilter={runFilter}
        onChangeRunFilter={setRunFilter}
        search={search}
        onChangeSearch={setSearch}
        resultCount={filtered.length}
      />

      <div className={styles.list}>
        {filtered.length === 0 ? (
          <p className={styles.empty}>No decisions match the current filters.</p>
        ) : (
          filtered.map((entry) => <DecisionEntry key={entry.id} entry={entry} />)
        )}
      </div>

      {/* This backend's ContextStore persists decisions and ambiguity
          resolutions, but never a distinct "risk register" concept -- no
          RiskRegisterEntry type or endpoint exists. Rather than fabricate
          one, this section stays honest about that gap. */}
      <div className={styles.riskSection}>
        <h2 className={styles.riskHeading}>Risk Register</h2>
        <p className={styles.riskEmpty}>
          No risk register entries -- this backend's ContextStore tracks decisions and ambiguity resolutions only.
        </p>
      </div>
    </div>
  );
}
