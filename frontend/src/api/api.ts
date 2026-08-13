/**
 * Every fetch call in this app lives here -- components and hooks import
 * these functions, never `fetch` directly. Base URL is configurable via
 * VITE_API_BASE_URL; defaults to :8081 because that's where this
 * assessment's backend actually runs (its default :8080 was already held
 * by a pre-existing local Oracle listener -- see the main repo's README).
 */

import type {
  ApiResponse,
  DecisionLogResponse,
  GateResolutionResponse,
  PipelineStatus,
  RunLogEntry,
  ScenarioType,
  Stage,
} from "../types";

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:8081";

export type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; message: string; networkError: boolean };

async function request<T>(path: string, init?: RequestInit): Promise<ApiResult<T>> {
  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
      ...init,
    });
  } catch {
    // fetch itself threw -- the backend is unreachable, not just erroring
    return { ok: false, message: "Cannot reach the backend", networkError: true };
  }

  let body: ApiResponse<T> | null = null;
  try {
    body = (await res.json()) as ApiResponse<T>;
  } catch {
    // non-JSON body (e.g. a proxy error page) -- fall through to status check
  }

  if (!res.ok || !body || !body.success) {
    const message = body?.error?.message ?? `Request failed (HTTP ${res.status})`;
    return { ok: false, message, networkError: false };
  }

  return { ok: true, data: body.data as T };
}

export function getStatus(runId?: string): Promise<ApiResult<PipelineStatus>> {
  const query = runId ? `?runId=${encodeURIComponent(runId)}` : "";
  return request<PipelineStatus>(`/orchestration/status${query}`);
}

export function getDecisions(): Promise<ApiResult<DecisionLogResponse>> {
  return request<DecisionLogResponse>("/orchestration/decisions");
}

export function getRuns(): Promise<ApiResult<RunLogEntry[]>> {
  return request<RunLogEntry[]>("/orchestration/runs");
}

export function runScenario(type: ScenarioType): Promise<ApiResult<PipelineStatus>> {
  return request<PipelineStatus>(`/scenarios/${type}/run`, { method: "POST" });
}

export function resolveGate(
  stage: Stage,
  approved: boolean,
  approver: string,
  reason: string
): Promise<ApiResult<GateResolutionResponse>> {
  return request<GateResolutionResponse>(`/orchestration/gates/${stage}/approve`, {
    method: "POST",
    body: JSON.stringify({ approved, approver, reason }),
  });
}

/** Cheap liveness probe for the nav's connection indicator -- deliberately
 * independent of pipeline polling, since the two can be true independently
 * (backend up, no run active; or a run active but this specific fetch failing). */
export async function checkConnection(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE_URL}/actuator/health`);
    return res.ok;
  } catch {
    return false;
  }
}
