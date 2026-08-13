/**
 * FINAL QUALITY CHECK
 * ====================================================================
 *
 * 1. What is the one most opinionated design decision in this UI and why?
 *
 *    The dependency graph (PipelineGraph.tsx) deliberately does NOT put
 *    the sequential chain (REQUIREMENTS -> ARCHITECTURE -> TASK_PLANNING)
 *    and the parallel fan (IMPLEMENTATION / TESTING / DOCUMENTATION) in
 *    the same 3-column grid, even though that would have been easier and
 *    tidier. They represent genuinely different relationships -- sequence
 *    vs. concurrency -- and forcing shared column widths between them
 *    would visually claim an alignment (e.g. "TASK_PLANNING sits directly
 *    above IMPLEMENTATION") that isn't structurally true. The chain is an
 *    independent flex row; the fan is an independent CSS grid; a static
 *    SVG connector bridges them. More work, correct claim.
 *
 * 2. What did the skill constrain that I would have done differently?
 *
 *    Body text sits at 13px, not the skill's generic 16px-minimum body
 *    rule (Quick Reference SS6, `readable-font-size`). That rule exists to
 *    prevent iOS auto-zoom on mobile form inputs -- irrelevant here, since
 *    this is a desktop-only internal tool. The skill's OWN more specific
 *    "Data-Dense Dashboard" style entry (styles.csv #28) recommends
 *    12-14px for exactly this product category, and Quick Reference SS4
 *    (`style-match`) says to match the specific category over a generic
 *    default -- so 13px follows the skill's own priority order, not a
 *    deviation from it. Left as a documented judgment call regardless.
 *
 * 3. What would a senior engineer change if this went to production?
 *
 *    - Gate detection is currently a heuristic (a gated stage sitting in
 *      RUNNING is *probably* waiting on human approval, since guardrail
 *      validation and the stage handler itself resolve in milliseconds).
 *      That's honest about what the real API exposes today, but a
 *      production build should add a real gate-state field to
 *      GET /orchestration/status instead of inferring it from timing.
 *    - 2s polling should become a WebSocket/SSE push -- fine for a demo,
 *      wasteful and laggy under real concurrent load.
 *    - MAX_ATTEMPTS = 3 (StageNode.tsx) duplicates
 *      orchestration.retry.max-attempts from the backend's own
 *      application.yml. One source of truth, served by the API, not two.
 *    - "dashboard-operator" is a hardcoded approver string -- there's no
 *      auth layer on either side of this assessment, gates included.
 *
 * 4. What makes this look like it was built by a human with strong opinions?
 *
 *    FAILED nodes carry actual texture -- a diagonal hazard-stripe
 *    background on the badge chip, not just a red dot -- because "must
 *    look wrong even in grayscale" was taken literally, not decoratively.
 *    Gate approval requires a second click with the button relabeling
 *    itself ("Confirm approve?") instead of a browser-style modal,
 *    because modals break flow state in an ops tool and a real engineer
 *    building this for daily use would resent one. And the type scale
 *    bottoms out at 10px for the least-important text (muted history
 *    timestamps) -- a generic template rarely goes below 12px because it
 *    "feels risky"; a dashboard whose entire purpose is information
 *    density doesn't get to be scared of small text where it's earned.
 */

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./styles/global.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
