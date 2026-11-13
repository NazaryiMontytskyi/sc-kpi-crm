import { Component, lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import classes from './LoginBackground.module.css';

type BackgroundModule = { default: ComponentType };

function Placeholder() {
  return <div className={classes.placeholder} aria-hidden="true" data-testid="login-background-placeholder" />;
}

/** A decorative layer must never break the page: on any error it falls back to the static placeholder. */
class BackgroundBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch() {
    // Intentionally silent: the background is decoration; the sign-in form keeps working without it.
  }

  render() {
    return this.state.failed ? <Placeholder /> : this.props.children;
  }
}

/**
 * Wraps a lazily imported background so the sign-in form never waits for it: until the chunk arrives (or if it
 * fails) a static dark placeholder is shown. Exposed as a factory so tests can inject a never-resolving import.
 */
export function createLazyBackground(load: () => Promise<BackgroundModule>) {
  const Background = lazy(load);
  return function LazyBackground() {
    return (
      <BackgroundBoundary>
        <Suspense fallback={<Placeholder />}>
          <Background />
        </Suspense>
      </BackgroundBoundary>
    );
  };
}

/** Sign-in background (BG-*), loaded as a separate chunk together with the photo metadata. */
export const LoginBackground = createLazyBackground(() => import('./BackgroundLayer'));
