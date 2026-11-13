import { act, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { renderWithProviders } from '../../../test/render';
import { resetMotionPauseMemory } from '../hooks/useMotionPreference';
import { REDUCED_MOTION_QUERY } from '../hooks/usePrefersReducedMotion';
import { COARSE_POINTER_QUERY } from '../layoutQueries';
import { loginPageConfig } from '../loginPage.config';
import { particleCount } from '../particles';
import { stubMediaQueries } from '../testing/mediaStub';
import { stubViewport } from '../testing/viewportStub';
import { MotionToggle } from './MotionToggle';
import { Particles } from './Particles';

const PAUSE_KEY = loginPageConfig.motion.pauseStorageKey;
const dots = () => screen.queryByTestId('login-particles')?.children.length ?? 0;

function setHidden(hidden: boolean) {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => (hidden ? 'hidden' : 'visible') });
  act(() => {
    document.dispatchEvent(new Event('visibilitychange'));
  });
}

describe('Particles (AMB-2)', { timeout: 20000 }, () => {
  let restore: () => void = () => {};
  beforeEach(() => {
    localStorage.removeItem(PAUSE_KEY);
    resetMotionPauseMemory();
  });
  afterEach(() => {
    restore();
    restore = () => {};
    localStorage.removeItem(PAUSE_KEY);
    setHidden(false);
  });

  it('renders the configured 15-30 particles on desktop', () => {
    const viewport = stubViewport(1280);
    restore = viewport.restore;
    renderWithProviders(<Particles />);
    expect(dots()).toBe(particleCount(false));
    expect(dots()).toBeGreaterThanOrEqual(loginPageConfig.particles.minCount);
    expect(dots()).toBeLessThanOrEqual(loginPageConfig.particles.maxCount);
  });

  it('halves the count on a phone viewport and with a coarse pointer', () => {
    const viewport = stubViewport(375);
    renderWithProviders(<Particles />);
    expect(dots()).toBe(Math.ceil(particleCount(false) / 2));
    viewport.restore();

    const media = stubMediaQueries([COARSE_POINTER_QUERY]);
    restore = media.restore;
    renderWithProviders(<Particles variant="snow" />);
    expect(screen.getAllByTestId('login-particles')[1].children).toHaveLength(Math.ceil(particleCount(false) / 2));
  });

  it('is decorative: hidden from assistive technology, nothing focusable', () => {
    renderWithProviders(<Particles />);
    const layer = screen.getByTestId('login-particles');
    expect(layer).toHaveAttribute('aria-hidden', 'true');
    expect(layer.querySelectorAll('[tabindex], button, a')).toHaveLength(0);
    expect(layer).toHaveAttribute('data-variant', 'default');
  });

  it('renders the snow variant through the same layer (SEA-1)', () => {
    renderWithProviders(<Particles variant="snow" />);
    expect(screen.getByTestId('login-particles')).toHaveAttribute('data-variant', 'snow');
    expect(dots()).toBe(particleCount(false));
  });

  it('renders no particle nodes under reduced motion', () => {
    const media = stubMediaQueries([REDUCED_MOTION_QUERY]);
    restore = media.restore;
    renderWithProviders(<Particles />);
    expect(screen.queryByTestId('login-particles')).not.toBeInTheDocument();
    act(() => media.set(REDUCED_MOTION_QUERY, false));
    expect(dots()).toBe(particleCount(false));
  });

  it('removes the particles while paused and brings them back on resume', () => {
    renderWithProviders(
      <>
        <Particles />
        <MotionToggle />
      </>,
    );
    expect(dots()).toBe(particleCount(false));
    fireEvent.click(screen.getByTestId('login-motion-toggle'));
    expect(screen.queryByTestId('login-particles')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('login-motion-toggle'));
    expect(dots()).toBe(particleCount(false));
  });

  it('freezes the animation while the tab is hidden', () => {
    renderWithProviders(<Particles />);
    const layer = screen.getByTestId('login-particles');
    expect(layer).toHaveAttribute('data-running', 'true');
    setHidden(true);
    expect(layer).toHaveAttribute('data-running', 'false');
    setHidden(false);
    expect(layer).toHaveAttribute('data-running', 'true');
  });
});
