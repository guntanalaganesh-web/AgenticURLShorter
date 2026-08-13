import { ConnectionIndicator } from "./ConnectionIndicator";
import styles from "./Nav.module.css";

export type View = "pipeline" | "scenarios" | "decisions";

interface NavProps {
  view: View;
  onNavigate: (view: View) => void;
}

const ITEMS: Array<{ id: View; label: string; index: string }> = [
  { id: "pipeline", label: "Pipeline Monitor", index: "01" },
  { id: "scenarios", label: "Scenario Runner", index: "02" },
  { id: "decisions", label: "Decision Log", index: "03" },
];

export function Nav({ view, onNavigate }: NavProps) {
  return (
    <nav className={styles.nav} aria-label="Primary">
      <div className={styles.brand}>
        <span className={styles.brandText}>
          orchestration
          <span className={styles.brandSub}>schwab-assessment / url-shortener</span>
        </span>
      </div>

      <div className={styles.items}>
        {ITEMS.map((item) => (
          <button
            key={item.id}
            className={`${styles.item} ${view === item.id ? styles.itemActive : ""}`}
            onClick={() => onNavigate(item.id)}
            aria-current={view === item.id ? "page" : undefined}
          >
            <span className={styles.itemIndex}>{item.index}</span>
            {item.label}
          </button>
        ))}
      </div>

      <div className={styles.spacer} />

      <div className={styles.footer}>
        <ConnectionIndicator />
      </div>
    </nav>
  );
}
