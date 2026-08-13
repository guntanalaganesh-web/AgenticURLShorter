import { useState } from "react";
import type { Confidence, Stage } from "../../types";
import { formatDateTime } from "../../utils/format";
import styles from "./DecisionEntry.module.css";

export interface LogEntry {
  id: string;
  runId: string;
  stage: Stage;
  timestamp: string;
  headline: string;
  body: string;
  alternatives: string[];
  kind: "decision" | "ambiguity";
  confidence?: Confidence;
}

export function DecisionEntry({ entry }: { entry: LogEntry }) {
  const [expanded, setExpanded] = useState(false);
  const isAmbiguity = entry.kind === "ambiguity";

  return (
    <article className={`${styles.entry} ${isAmbiguity ? styles.ambiguity : ""}`}>
      <div className={styles.top}>
        <div className={styles.stageAndFlag}>
          <span className={styles.stage}>{entry.stage.replace("_", " ")}</span>
          {isAmbiguity && (
            <span className={styles.flag}>
              <svg viewBox="0 0 10 10" fill="none" aria-hidden="true">
                <path d="M5 1 L9 8.5 H1 Z" stroke="currentColor" strokeWidth="1" strokeLinejoin="round" />
                <line x1="5" y1="4" x2="5" y2="5.8" stroke="currentColor" strokeWidth="1" strokeLinecap="round" />
                <circle cx="5" cy="7" r="0.5" fill="currentColor" />
              </svg>
              ambiguity resolved
            </span>
          )}
          {entry.confidence && (
            <span className={`${styles.confidence} ${styles[`confidence${entry.confidence}`]}`}>{entry.confidence} confidence</span>
          )}
        </div>
        <time className={styles.timestamp} dateTime={entry.timestamp}>
          {formatDateTime(entry.timestamp)}
        </time>
      </div>

      <h3 className={styles.headline}>{entry.headline}</h3>
      <p className={styles.body}>{entry.body}</p>

      {entry.alternatives.length > 0 && (
        <>
          <button className={styles.expandToggle} onClick={() => setExpanded((v) => !v)} aria-expanded={expanded}>
            {expanded ? "− hide" : "+ show"} {entry.alternatives.length} alternative{entry.alternatives.length === 1 ? "" : "s"} considered
          </button>
          {expanded && (
            <ul className={styles.alternatives}>
              {entry.alternatives.map((alt, i) => (
                <li key={i}>{alt}</li>
              ))}
            </ul>
          )}
        </>
      )}
    </article>
  );
}
