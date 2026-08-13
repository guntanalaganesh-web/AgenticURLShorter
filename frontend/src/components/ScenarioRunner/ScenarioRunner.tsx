import { useCallback, useEffect, useState } from "react";
import { getRuns, runScenario } from "../../api/api";
import { SCENARIOS } from "../../types";
import type { RunLogEntry, ScenarioType } from "../../types";
import { ErrorBanner } from "../shared/ErrorBanner";
import { ScenarioCard } from "./ScenarioCard";
import { RunHistoryList } from "./RunHistoryList";
import styles from "./ScenarioRunner.module.css";

interface ScenarioRunnerProps {
  onRunStarted: () => void;
  onSelectRun: (runId: string) => void;
}

export function ScenarioRunner({ onRunStarted, onSelectRun }: ScenarioRunnerProps) {
  const [runs, setRuns] = useState<RunLogEntry[]>([]);
  const [runningType, setRunningType] = useState<ScenarioType | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadRuns = useCallback(async () => {
    const result = await getRuns();
    if (result.ok) {
      setRuns(result.data);
    }
    // A failed history refresh isn't worth an error banner over -- the
    // cards and Run buttons still work without it.
  }, []);

  useEffect(() => {
    loadRuns();
  }, [loadRuns]);

  async function handleRun(type: ScenarioType) {
    setError(null);
    setRunningType(type);
    onRunStarted();

    const result = await runScenario(type);
    setRunningType(null);
    if (!result.ok) {
      setError(`${type} run failed to start: ${result.message}`);
    }
    loadRuns();
  }

  const lastRunByType = new Map<string, RunLogEntry>();
  for (const run of runs) {
    if (!lastRunByType.has(run.scenarioType)) {
      lastRunByType.set(run.scenarioType, run);
    }
  }

  return (
    <div className={styles.view}>
      <h1 className={styles.title}>Scenario Runner</h1>

      {error && <ErrorBanner message={error} onRetry={() => setError(null)} />}

      <div className={styles.cards}>
        {SCENARIOS.map((scenario) => (
          <ScenarioCard
            key={scenario.type}
            name={scenario.name}
            description={scenario.description}
            requirement={scenario.requirement}
            lastRun={lastRunByType.get(scenario.type)}
            isRunning={runningType === scenario.type}
            onRun={() => handleRun(scenario.type)}
          />
        ))}
      </div>

      <RunHistoryList runs={runs} onSelectRun={onSelectRun} />
    </div>
  );
}
