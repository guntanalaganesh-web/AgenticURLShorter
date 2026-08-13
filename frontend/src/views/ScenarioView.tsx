import { ScenarioRunner } from "../components/ScenarioRunner/ScenarioRunner";

interface ScenarioViewProps {
  onRunStarted: () => void;
  onSelectRun: (runId: string) => void;
}

export function ScenarioView({ onRunStarted, onSelectRun }: ScenarioViewProps) {
  return <ScenarioRunner onRunStarted={onRunStarted} onSelectRun={onSelectRun} />;
}
