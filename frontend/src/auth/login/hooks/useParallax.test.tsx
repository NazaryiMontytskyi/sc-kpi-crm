import { MantineProvider } from '@mantine/core';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { Profiler } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { changeLanguage } from '../../../i18n';
import { renderWithProviders } from '../../../test/render';
import { cssVariablesResolver, theme } from '../../../theme/theme';
import { LoginCard } from '../components/LoginCard';
import { LoginShell } from '../components/LoginShell';
import { FINE_POINTER_QUERY } from '../layoutQueries';
import { loginPageConfig } from '../loginPage.config';
import { stubMediaQueries } from '../testing/mediaStub';
import type { LoginFormState } from './useLoginForm';
import { resetMotionPauseMemory } from './useMotionPreference';
import { REDUCED_MOTION_QUERY } from './usePrefersReducedMotion';

const PAUSE_KEY = loginPageConfig.motion.pauseStorageKey;

const form: LoginFormState = {
  status: 'anonymous',
  mustChangePassword: false,
  login: '',
  setLogin: () => {},
  password: '',
  setPassword: () => {},
  submit: (e) => e.preventDefault(),
  problem: null,
  pending: false,
  disabled: true,
  failureCount: 0,
  signedInByForm: false,
};

/** requestAnimationFrame that queues callbacks until `flush`. */
function stubAnimationFrames() {
  let id = 0;
  const queue = new Map<number, FrameRequestCallback>();
  const request = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
    queue.set(++id, cb);
    return id;
  });
  const cancel = vi.spyOn(window, 'cancelAnimationFrame').mockImplementation((handle) => {
    queue.delete(handle);
  });
  return {
    request,
    cancel,
    pending: () => [...queue.keys()],
    flush(frames = 1) {
      for (let i = 0; i < frames; i++) {
        const batch = [...queue.entries()];
        queue.clear();
        batch.forEach(([, cb]) => cb(performance.now()));
      }
    },
  };
}

function pointerMove(target: EventTarget, x: number, y: number, pointerType = 'mouse') {
  const Ctor = (window.PointerEvent ?? window.MouseEvent) as typeof MouseEvent;
  const event = new Ctor('pointermove', { clientX: x, clientY: y, bubbles: true });
  Object.defineProperty(event, 'pointerType', { value: pointerType });
  act(() => {
    target.dispatchEvent(event);
  });
}

function setHidden(hidden: boolean) {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => (hidden ? 'hidden' : 'visible') });
  act(() => {
    document.dispatchEvent(new Event('visibilitychange'));
  });
}

function renderShell() {
  return renderWithProviders(
    <LoginShell background={<div data-testid="bg" />}>
      <LoginCard form={form} />
    </LoginShell>,
  );
}

const listenerTypes = (spy: { mock: { calls: unknown[][] } }) => spy.mock.calls.map((call) => call[0]);

/** Listener types registered on one element (React itself listens on the root container, not on the card). */
function typesAddedOn(spy: { mock: { calls: unknown[][]; contexts: unknown[] } }, element: Element) {
  return spy.mock.calls.filter((_, i) => spy.mock.contexts[i] === element).map((call) => call[0]);
}

describe('mouse parallax and cursor light (AMB-1, AMB-3)', { timeout: 20000 }, () => {
  let media: ReturnType<typeof stubMediaQueries>;
  beforeEach(async () => {
    localStorage.removeItem(PAUSE_KEY);
    resetMotionPauseMemory();
    await changeLanguage('uk');
  });
  afterEach(() => {
    media?.restore();
    localStorage.removeItem(PAUSE_KEY);
    setHidden(false);
    vi.restoreAllMocks();
  });

  describe.each([
    ['a coarse / touch pointer', [] as string[], false],
    ['reduced motion', [FINE_POINTER_QUERY, REDUCED_MOTION_QUERY], false],
    ['the user pause', [FINE_POINTER_QUERY], true],
  ])('with %s', (_, queries, paused) => {
    it('registers no pointermove listener and requests no animation frame (spec §9.7, §9.8)', () => {
      if (paused) localStorage.setItem(PAUSE_KEY, 'true');
      media = stubMediaQueries(queries);
      const frames = stubAnimationFrames();
      const windowAdd = vi.spyOn(window, 'addEventListener');
      const elementAdd = vi.spyOn(HTMLElement.prototype, 'addEventListener');
      renderShell();

      const card = screen.getByTestId('login-card').querySelector('section')!;
      pointerMove(window, 10, 10);
      pointerMove(card, 20, 20);
      expect(listenerTypes(windowAdd)).not.toContain('pointermove');
      expect(typesAddedOn(elementAdd, card)).not.toContain('pointermove');
      expect(frames.request).not.toHaveBeenCalled();
      expect(screen.queryByTestId('login-card-light')).not.toBeInTheDocument();
    });
  });

  it('moves the background against and the hero with the pointer through CSS variables, without re-rendering', () => {
    media = stubMediaQueries([FINE_POINTER_QUERY]);
    const frames = stubAnimationFrames();
    let renders = 0;
    renderWithProviders(
      <Profiler id="shell" onRender={() => renders++}>
        <LoginShell background={<div data-testid="bg" />}>
          <LoginCard form={form} />
        </LoginShell>
      </Profiler>,
    );
    const before = renders;
    const hero = screen.getByTestId('login-hero');
    const layer = screen.getByTestId('bg').parentElement!;

    pointerMove(window, window.innerWidth, window.innerHeight);
    frames.flush(400);
    expect(layer.style.getPropertyValue('--login-parallax-x')).toBe('-12.00px');
    expect(layer.style.getPropertyValue('--login-parallax-y')).toBe('-12.00px');
    expect(hero.style.getPropertyValue('--login-parallax-x')).toBe('6.00px');
    expect(hero.style.getPropertyValue('--login-parallax-y')).toBe('6.00px');
    // Settled: the loop sleeps until the pointer moves again.
    expect(frames.pending()).toEqual([]);

    pointerMove(window, 0, 0);
    frames.flush(3);
    expect(parseFloat(layer.style.getPropertyValue('--login-parallax-x'))).toBeLessThan(0);
    expect(renders).toBe(before);
  });

  it('ignores touch and pen pointers on hybrid devices', () => {
    media = stubMediaQueries([FINE_POINTER_QUERY]);
    const frames = stubAnimationFrames();
    renderShell();
    pointerMove(window, 0, 0, 'touch');
    pointerMove(window, 0, 0, 'pen');
    expect(frames.request).not.toHaveBeenCalled();
  });

  it('stops the loop while the tab is hidden and resumes when it is visible again', () => {
    media = stubMediaQueries([FINE_POINTER_QUERY]);
    const frames = stubAnimationFrames();
    renderShell();
    pointerMove(window, 0, 0);
    const [pending] = frames.pending();
    expect(pending).toBeDefined();

    setHidden(true);
    expect(frames.cancel).toHaveBeenCalledWith(pending);
    expect(frames.pending()).toEqual([]);
    pointerMove(window, 5, 5);
    expect(frames.pending()).toEqual([]);

    setHidden(false);
    expect(frames.pending()).toHaveLength(1);
  });

  it('removes every listener and cancels the pending frames on unmount (spec §9.13)', () => {
    media = stubMediaQueries([FINE_POINTER_QUERY]);
    const frames = stubAnimationFrames();
    const windowAdd = vi.spyOn(window, 'addEventListener');
    const windowRemove = vi.spyOn(window, 'removeEventListener');
    const documentAdd = vi.spyOn(document, 'addEventListener');
    const documentRemove = vi.spyOn(document, 'removeEventListener');
    const elementAdd = vi.spyOn(HTMLElement.prototype, 'addEventListener');
    const elementRemove = vi.spyOn(HTMLElement.prototype, 'removeEventListener');
    const view = renderShell();
    const card = screen.getByTestId('login-card').querySelector('section')!;

    pointerMove(window, 30, 30);
    pointerMove(card, 40, 40);
    const pending = frames.pending();
    expect(pending.length).toBe(2);
    view.unmount();

    for (const id of pending) expect(frames.cancel).toHaveBeenCalledWith(id);
    const added = (spy: typeof windowAdd, type: string) => spy.mock.calls.filter((c) => c[0] === type).map((c) => c[1]);
    for (const listener of added(windowAdd, 'pointermove')) {
      expect(windowRemove).toHaveBeenCalledWith('pointermove', listener);
    }
    expect(added(windowAdd, 'pointermove')).toHaveLength(1);
    for (const type of ['pointermove', 'pointerleave']) {
      const listeners = elementAdd.mock.calls
        .filter((call, i) => call[0] === type && elementAdd.mock.contexts[i] === card)
        .map((call) => call[1]);
      expect(listeners).toHaveLength(1);
      for (const listener of listeners) expect(elementRemove).toHaveBeenCalledWith(type, listener);
    }
    for (const listener of added(documentAdd as unknown as typeof windowAdd, 'visibilitychange')) {
      expect(documentRemove).toHaveBeenCalledWith('visibilitychange', listener);
    }
  });

  it('lights the card under the pointer and fades it out when the pointer leaves', () => {
    media = stubMediaQueries([FINE_POINTER_QUERY]);
    const frames = stubAnimationFrames();
    renderShell();
    const card = screen.getByTestId('login-card').querySelector('section')!;
    expect(screen.getByTestId('login-card-light')).toHaveAttribute('aria-hidden', 'true');

    pointerMove(card, 40, 25);
    frames.flush();
    expect(card).toHaveAttribute('data-light', 'on');
    expect(card.style.getPropertyValue('--login-light-x')).toBe('40px');
    expect(card.style.getPropertyValue('--login-light-y')).toBe('25px');

    fireEvent.pointerLeave(card);
    expect(card).not.toHaveAttribute('data-light');
  });

  it('freezes the offsets when the success transition starts instead of snapping back (TR-3)', () => {
    media = stubMediaQueries([FINE_POINTER_QUERY]);
    const frames = stubAnimationFrames();
    const windowRemove = vi.spyOn(window, 'removeEventListener');
    // Same provider tree on every render, so `rerender` only changes the phase.
    const shell = (phase: 'idle' | 'exiting') => (
      <MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver}>
        <MemoryRouter>
          <LoginShell background={<div data-testid="bg" />} phase={phase}>
            <LoginCard form={form} />
          </LoginShell>
        </MemoryRouter>
      </MantineProvider>
    );
    const view = render(shell('idle'));
    const layer = screen.getByTestId('bg').parentElement!;
    const hero = screen.getByTestId('login-hero');
    pointerMove(window, window.innerWidth, window.innerHeight);
    frames.flush(400);
    pointerMove(window, 0, 0);
    frames.flush(2);
    const offset = layer.style.getPropertyValue('--login-parallax-x');
    const heroOffset = hero.style.getPropertyValue('--login-parallax-x');
    expect(parseFloat(offset)).toBeLessThan(0);
    const [pending] = frames.pending();
    expect(pending).toBeDefined();

    view.rerender(shell('exiting'));
    // Same offsets, no running loop, no pointer listener.
    expect(layer.style.getPropertyValue('--login-parallax-x')).toBe(offset);
    expect(hero.style.getPropertyValue('--login-parallax-x')).toBe(heroOffset);
    expect(frames.cancel).toHaveBeenCalledWith(pending);
    expect(frames.pending()).toEqual([]);
    expect(windowRemove.mock.calls.map((call) => call[0])).toContain('pointermove');
    frames.request.mockClear();
    pointerMove(window, 200, 200);
    expect(frames.request).not.toHaveBeenCalled();
    expect(layer.style.getPropertyValue('--login-parallax-x')).toBe(offset);
  });

  it('turns everything off as soon as the user pauses motion', () => {
    media = stubMediaQueries([FINE_POINTER_QUERY]);
    const frames = stubAnimationFrames();
    renderShell();
    pointerMove(window, 0, 0);
    frames.flush(2);
    act(() => {
      fireEvent.click(screen.getByTestId('login-motion-toggle'));
    });
    const layer = screen.getByTestId('bg').parentElement!;
    expect(layer.style.getPropertyValue('--login-parallax-x')).toBe('');
    expect(screen.queryByTestId('login-card-light')).not.toBeInTheDocument();
    frames.request.mockClear();
    pointerMove(window, 100, 100);
    expect(frames.request).not.toHaveBeenCalled();
  });
});
