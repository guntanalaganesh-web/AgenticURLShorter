import { useEffect, useState } from "react";
import { checkConnection } from "../../api/api";
import styles from "./ConnectionIndicator.module.css";

const CHECK_INTERVAL_MS = 5000;

/**
 * Lives in the nav, not a full-page error -- the backend being down
 * shouldn't block browsing the (now-empty) UI, just be visible at a glance.
 */
export function ConnectionIndicator() {
  const [connected, setConnected] = useState<boolean | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function check() {
      const result = await checkConnection();
      if (!cancelled) setConnected(result);
    }

    check();
    const id = setInterval(check, CHECK_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  if (connected === null) {
    return null;
  }

  return (
    <div className={`${styles.indicator} ${connected ? styles.connected : styles.disconnected}`}>
      <span className={styles.dot} aria-hidden="true" />
      {connected ? "backend connected" : "backend unreachable"}
    </div>
  );
}
