import type { CSSProperties } from 'react';
import { loginPageConfig } from '../loginPage.config';
import classes from './AuroraFallback.module.css';

const style = { '--login-aurora-drift': `${loginPageConfig.aurora.driftMs}ms` } as CSSProperties;

/** BG-10: blurred brand-palette blobs drifting slowly; purely decorative. */
export function AuroraFallback() {
  return (
    <div className={classes.aurora} style={style} data-testid="login-aurora" aria-hidden="true">
      <span className={`${classes.blob} ${classes.blobA}`} />
      <span className={`${classes.blob} ${classes.blobB}`} />
      <span className={`${classes.blob} ${classes.blobC}`} />
    </div>
  );
}
