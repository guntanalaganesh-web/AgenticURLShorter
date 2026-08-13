import { useState } from "react";
import { Nav } from "./components/shared/Nav";
import type { View } from "./components/shared/Nav";
import { PipelineView } from "./views/PipelineView";
import { ScenarioView } from "./views/ScenarioView";
import { DecisionView } from "./views/DecisionView";
import { usePipelinePolling } from "./hooks/usePipelinePolling";
import styles from "./App.module.css";

export function App() {
  const [view, setView] = useState<View>("pipeline");
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const { status, error, isPolling, start, refresh } = usePipelinePolling();

  function handleRunStarted() {
    setView("pipeline");
    start();
  }

  function handleSelectRun(runId: string) {
    setSelectedRunId(runId);
    setView("decisions");
  }

  return (
    <div className={styles.shell}>
      <Nav view={view} onNavigate={setView} />
      <main className={styles.main}>
        <div className={styles.mainInner}>
          {view === "pipeline" && <PipelineView status={status} error={error} isPolling={isPolling} onRefresh={refresh} />}
          {view === "scenarios" && <ScenarioView onRunStarted={handleRunStarted} onSelectRun={handleSelectRun} />}
          {view === "decisions" && <DecisionView selectedRunId={selectedRunId} />}
        </div>
      </main>
    </div>
  );
}
