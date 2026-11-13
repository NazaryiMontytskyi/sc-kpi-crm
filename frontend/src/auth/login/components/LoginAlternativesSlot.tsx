import type { ReactNode } from 'react';
import classes from './LoginCard.module.css';

/**
 * CARD-9: reserved place below the submit button for future sign-in alternatives ("Увійти через KPI ID", Telegram).
 * Renders nothing today. It is the last item of the card's form stack, so filling it later only appends a row and
 * changes neither the card's classes nor the layout of the fields above it.
 */
export function LoginAlternativesSlot({ children }: { children?: ReactNode }) {
  if (children === undefined || children === null || children === false) return null;
  return (
    <div className={classes.alternatives} data-testid="login-alternatives">
      {children}
    </div>
  );
}
