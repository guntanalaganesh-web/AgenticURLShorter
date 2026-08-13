interface FanConnectorProps {
  /** "out" fans one line into three (before the parallel row); "in" merges
   * three lines back into one (after it). Same shape, vertically mirrored. */
  direction: "out" | "in";
  /** Highlights the connector once the branch point has actually been
   * reached by execution, rather than rendering it permanently "active". */
  active: boolean;
}

/**
 * Static SVG connector expressing the graph's one real structural fork:
 * TASK_PLANNING fans out to three concurrent stages, which fan back in to
 * RELEASE_READINESS. Coordinates are fixed proportions of a 3-column row
 * (not measured from the DOM) since the layout above and below it is
 * always exactly three equal columns -- no ResizeObserver needed for a
 * shape that never actually varies.
 */
export function FanConnector({ direction, active }: FanConnectorProps) {
  const stroke = active ? "var(--color-active)" : "var(--color-border)";
  const paths =
    direction === "out" ? (
      <>
        <line x1="150" y1="0" x2="150" y2="20" />
        <line x1="50" y1="20" x2="250" y2="20" />
        <line x1="50" y1="20" x2="50" y2="44" />
        <line x1="150" y1="20" x2="150" y2="44" />
        <line x1="250" y1="20" x2="250" y2="44" />
      </>
    ) : (
      <>
        <line x1="50" y1="0" x2="50" y2="24" />
        <line x1="150" y1="0" x2="150" y2="24" />
        <line x1="250" y1="0" x2="250" y2="24" />
        <line x1="50" y1="24" x2="250" y2="24" />
        <line x1="150" y1="24" x2="150" y2="44" />
      </>
    );

  return (
    <svg viewBox="0 0 300 44" preserveAspectRatio="none" aria-hidden="true" style={{ width: "100%", height: 44, display: "block" }}>
      <g stroke={stroke} strokeWidth="1.5" fill="none">
        {paths}
      </g>
    </svg>
  );
}
