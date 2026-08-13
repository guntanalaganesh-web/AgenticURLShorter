import { DecisionLog } from "../components/DecisionLog/DecisionLog";

interface DecisionViewProps {
  selectedRunId: string | null;
}

export function DecisionView({ selectedRunId }: DecisionViewProps) {
  return <DecisionLog selectedRunId={selectedRunId} />;
}
