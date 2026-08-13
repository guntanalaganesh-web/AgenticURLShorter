import { useCallback, useEffect, useRef, useState } from "react";
import { getStatus } from "../api/api";
import type { PipelineStatus } from "../types";

const POLL_INTERVAL_MS = 2000;

/**
 * Polls GET /orchestration/status every 2s while a run is active, stopping
 * automatically once overallStatus reaches a terminal value. The backend's
 * real overallStatus is RUNNING | COMPLETED | HALTED (HALTED covers both
 * a policy/gate-triggered stop and exhausted retries -- the engine doesn't
 * distinguish "FAILED" from "SAFE_STOPPED" as separate top-level states,
 * so both map to HALTED here; the per-stage StatusBadge and haltReason
 * text carry the finer distinction).
 *
 * Always polls the no-runId "current run" endpoint rather than a specific
 * runId: this app only ever watches one live run at a time, and the
 * server already tracks "current" for exactly this case.
 */
export function usePipelinePolling() {
  const [status, setStatus] = useState<PipelineStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isPolling, setIsPolling] = useState(false);
  const pollingRef = useRef(false);
  const timerRef = useRef<number | undefined>(undefined);

  const fetchOnce = useCallback(async (): Promise<PipelineStatus | null> => {
    const result = await getStatus();
    if (result.ok) {
      setStatus(result.data);
      setError(null);
      return result.data;
    }
    setError(result.message);
    return null;
  }, []);

  const stop = useCallback(() => {
    pollingRef.current = false;
    setIsPolling(false);
    if (timerRef.current !== undefined) {
      window.clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
  }, []);

  const start = useCallback(() => {
    if (pollingRef.current) return;
    pollingRef.current = true;
    setIsPolling(true);

    const tick = async () => {
      if (!pollingRef.current) return;
      const data = await fetchOnce();
      if (!pollingRef.current) return;

      const terminal = data && (data.overallStatus === "COMPLETED" || data.overallStatus === "HALTED");
      if (terminal) {
        stop();
        return;
      }
      timerRef.current = window.setTimeout(tick, POLL_INTERVAL_MS);
    };
    tick();
  }, [fetchOnce, stop]);

  // On mount: check once for an already-active run (e.g. page reloaded
  // mid-run) and resume polling if so.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const data = await fetchOnce();
      if (!cancelled && data && data.overallStatus === "RUNNING") {
        start();
      }
    })();
    return () => {
      cancelled = true;
      stop();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { status, error, isPolling, start, stop, refresh: fetchOnce };
}
