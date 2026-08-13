# Orchestration Dashboard

Frontend for the agentic SDLC orchestration engine in the parent repo. See
[`src/main.tsx`](src/main.tsx) for the design rationale and final quality-check notes, and
[`package.json`](package.json) for the stack justification.

## Live

**https://frontend-nu-steel-80.vercel.app** -- deployed on Vercel, talking to a Render-hosted backend at
`https://schwab-orchestration.onrender.com`. See the parent repo's README ("How the live demo is
deployed") for how both sides are wired together and the free-tier expiration date to know about.

## Setup

```bash
npm install
npm run dev
```

Opens on `http://localhost:5173`. Requires the backend running (see the parent repo's
README) at `http://localhost:8081` by default -- override via `VITE_API_BASE_URL`
(copy `.env.example` to `.env.local` to set it).

## Build

```bash
npm run build   # type-checks (tsc -b) then builds to dist/
npm run preview # serve the production build locally
```

## What's real vs. simplified

Every data point in this dashboard comes from the live backend -- nothing is mocked or
fabricated. Two documented simplifications, both explained where they're implemented:

- **Gate detection is a heuristic**, not a dedicated API field: a gated stage
  (ARCHITECTURE / RELEASE_READINESS) showing `RUNNING` is treated as awaiting human
  approval, since the guardrail check and the stage handler itself resolve in
  milliseconds either side of it. See `GatePanel.tsx`'s header comment.
- **No risk register section has real data** -- the backend's `ContextStore` persists
  decisions and ambiguity resolutions only; the Decision Log view says so honestly
  rather than fabricating entries for a data source that doesn't exist.
