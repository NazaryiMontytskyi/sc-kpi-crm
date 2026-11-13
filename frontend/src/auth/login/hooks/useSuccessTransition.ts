import { useCallback, useEffect, useRef, useState } from 'react';
import { loginPageConfig } from '../loginPage.config';

export interface SuccessTransition {
  /** The transition is over (or was cut short): navigate now. */
  done: boolean;
  /** Ends the transition early, e.g. when the full-screen fade has finished. Idempotent. */
  finish: () => void;
}

/**
 * TR-3 exit after a successful sign-in. While `active`, a hard timeout of `success.transitionMs` (<= 900 ms) ends
 * the transition even if no animation event ever arrives (hidden tab, missing CSS), so navigation is never later
 * than that. A ref makes `finish` one-shot, so the page navigates exactly once. The timer is cleared on unmount.
 */
export function useSuccessTransition(active: boolean): SuccessTransition {
  const [done, setDone] = useState(false);
  const finished = useRef(false);
  const finish = useCallback(() => {
    if (finished.current) return;
    finished.current = true;
    setDone(true);
  }, []);

  useEffect(() => {
    if (!active) return;
    const timer = setTimeout(finish, loginPageConfig.success.transitionMs);
    return () => clearTimeout(timer);
  }, [active, finish]);

  return { done, finish };
}
