import { useCallback, useEffect, useState } from 'react';
import { loginPageConfig } from '../loginPage.config';

const PLAYED = '1';

function sessionStore(): Storage | undefined {
  return typeof window === 'undefined' ? undefined : window.sessionStorage;
}

/**
 * Whether the intro may play: not yet played in this browser session. Documented choice (TR-2): when
 * sessionStorage is missing or throws, the intro is skipped, because it could not be limited to once per session.
 */
export function introPending(): boolean {
  try {
    const store = sessionStore();
    if (!store) return false;
    return store.getItem(loginPageConfig.intro.sessionStorageKey) === null;
  } catch {
    return false;
  }
}

function markIntroPlayed() {
  try {
    sessionStore()?.setItem(loginPageConfig.intro.sessionStorageKey, PLAYED);
  } catch {
    // Storage failed after a successful read: the intro may play again on the next mount; nothing else breaks.
  }
}

export interface IntroSplashState {
  visible: boolean;
  dismiss: () => void;
}

/**
 * TR-1 / TR-2: the intro splash plays once per browser session. Decided on the first render (so it covers the very
 * first paint) from `eligible` (no reduced motion, no user pause, not signed in) and the sessionStorage flag, which
 * is set as soon as it shows. It disappears for good when dismissed or once it stops being eligible.
 */
export function useIntroSplash(eligible: boolean): IntroSplashState {
  const [visible, setVisible] = useState(() => eligible && introPending());
  if (visible && !eligible) setVisible(false);

  useEffect(() => {
    if (visible) markIntroPlayed();
  }, [visible]);

  const dismiss = useCallback(() => setVisible(false), []);
  return { visible: visible && eligible, dismiss };
}
