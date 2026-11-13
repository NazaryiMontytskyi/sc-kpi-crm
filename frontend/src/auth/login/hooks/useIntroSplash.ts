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

type IntroDecision = 'pending' | 'show' | 'skip';

function decide(eligible: boolean): IntroDecision {
  return eligible && introPending() ? 'show' : 'skip';
}

/**
 * TR-1 / TR-2: the intro splash plays once per browser session. Nothing is decided (and the sessionStorage flag is
 * not touched) until `ready`, i.e. until the session bootstrap has answered: a user who turns out to be signed in
 * never sees the splash and does not consume the once-per-session flag. The decision is then taken once from
 * `eligible` (no reduced motion, no user pause, not signed in) and the flag, which is set as soon as the splash shows.
 * It disappears for good when dismissed or once it stops being eligible.
 */
export function useIntroSplash(eligible: boolean, ready = true): IntroSplashState {
  const [decision, setDecision] = useState<IntroDecision>(() => (ready ? decide(eligible) : 'pending'));
  let current = decision;
  if (current === 'pending' && ready) {
    current = decide(eligible);
    setDecision(current);
  } else if (current === 'show' && !eligible) {
    current = 'skip';
    setDecision(current);
  }
  const visible = current === 'show';

  useEffect(() => {
    if (visible) markIntroPlayed();
  }, [visible]);

  const dismiss = useCallback(() => setDecision('skip'), []);
  return { visible, dismiss };
}
