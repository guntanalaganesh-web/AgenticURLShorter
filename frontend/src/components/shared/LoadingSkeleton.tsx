import styles from "./LoadingSkeleton.module.css";

interface LoadingSkeletonProps {
  width?: string;
  height?: string;
  style?: React.CSSProperties;
}

/** Skeleton block for loads expected to take >300ms, per the skill's
 * progressive-loading rule -- never a spinner for content placeholders. */
export function LoadingSkeleton({ width = "100%", height = "16px", style }: LoadingSkeletonProps) {
  return <div className={styles.skeleton} style={{ width, height, ...style }} aria-hidden="true" />;
}
