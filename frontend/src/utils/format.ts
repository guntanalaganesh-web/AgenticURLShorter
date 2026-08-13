/** Compact duration formatting for the tight space a stage node has. */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60_000);
  const seconds = Math.round((ms % 60_000) / 1000);
  return `${minutes}m ${seconds}s`;
}

/** HH:MM:SS in the local timezone -- enough precision for a demo session,
 * full precision available via the title attribute wherever this is used. */
export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, {
    hour12: false,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
}

const STAGE_LABELS: Record<string, string> = {
  REQUIREMENTS: "Requirements",
  ARCHITECTURE: "Architecture",
  TASK_PLANNING: "Task Planning",
  IMPLEMENTATION: "Implementation",
  TESTING: "Testing",
  DOCUMENTATION: "Documentation",
  RELEASE_READINESS: "Release Readiness",
};

export function stageLabel(stage: string): string {
  return STAGE_LABELS[stage] ?? stage;
}
