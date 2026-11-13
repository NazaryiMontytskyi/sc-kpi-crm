import { MantineProvider } from '@mantine/core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { changeLanguage } from '../../i18n';
import uk from '../../i18n/locales/uk.json';
import { cssVariablesResolver, theme } from '../../theme/theme';
import { AuthProvider } from '../AuthProvider';
import * as authApi from '../authApi';
import { LoginPage } from '../LoginPage';
import { INTRO_TOTAL_MS } from './components/IntroSplash';
import { resetMotionPauseMemory } from './hooks/useMotionPreference';
import { FINE_POINTER_QUERY } from './layoutQueries';
import { loginPageConfig } from './loginPage.config';

/*
 * INC-062 hardening: keyboard order of the whole sign-in page (MAIN_PAGE_FEATURE.md §7) and a leak test of the full
 * flow (spec §9.13): intro, background, ambient motion, sign-in, success transition, navigation into the app. The real
 * lazy background is used (no encoded photos in unit tests, so it shows the aurora and the particles).
 */

vi.mock('../authApi', async (orig) => ({
  ...(await orig<typeof authApi>()),
  fetchSession: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(() => Promise.resolve()),
  changePasswordRequest: vi.fn(),
  changeLocaleRequest: vi.fn(),
}));

const INTRO_KEY = loginPageConfig.intro.sessionStorageKey;
const PAUSE_KEY = loginPageConfig.motion.pauseStorageKey;

const session = (): authApi.Session => ({
  user: { id: 'u1', displayName: 'fake.user', isAdmin: false, permissions: [] },
  locale: 'uk',
  mustChangePassword: false,
});

function renderAt(path: string) {
  // No garbage-collection timers of the query cache, so the timer count only reflects the page.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { gcTime: Infinity } },
  });
  return render(
    <MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver}>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/" element={<p>home page</p>} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    </MantineProvider>,
  );
}

/** matchMedia where the given queries match; counts the change listeners that are still attached. */
function trackMediaQueries(matching: string[]) {
  const original = window.matchMedia;
  const attached = new Map<string, Set<unknown>>();
  window.matchMedia = ((query: string) => {
    const own = attached.get(query) ?? new Set<unknown>();
    attached.set(query, own);
    return {
      matches: matching.includes(query),
      media: query,
      onchange: null,
      addEventListener: (_: string, l: unknown) => own.add(l),
      removeEventListener: (_: string, l: unknown) => own.delete(l),
      addListener: (l: unknown) => own.add(l),
      removeListener: (l: unknown) => own.delete(l),
      dispatchEvent: () => false,
    } as unknown as MediaQueryList;
  }) as typeof window.matchMedia;
  return {
    attached: () => [...attached.values()].reduce((sum, set) => sum + set.size, 0),
    restore: () => (window.matchMedia = original),
  };
}

/** Listeners still attached to window and document: `${target}:${type}` -> count. */
function trackGlobalListeners() {
  const live = new Map<string, Set<unknown>>();
  const spies = (['window', 'document'] as const).map((name) => {
    const target: EventTarget = name === 'window' ? window : document;
    const originalAdd = target.addEventListener.bind(target);
    const originalRemove = target.removeEventListener.bind(target);
    const add = vi.spyOn(target, 'addEventListener').mockImplementation((...args) => {
      const [type, listener, options] = args;
      const capture = typeof options === 'boolean' ? options : !!options?.capture;
      const key = `${name}:${type}:${capture}`;
      const set = live.get(key) ?? new Set<unknown>();
      set.add(listener);
      live.set(key, set);
      return originalAdd(...args);
    });
    const remove = vi.spyOn(target, 'removeEventListener').mockImplementation((...args) => {
      const [type, listener, options] = args;
      const capture = typeof options === 'boolean' ? options : !!options?.capture;
      live.get(`${name}:${type}:${capture}`)?.delete(listener);
      return originalRemove(...args);
    });
    return [add, remove];
  });
  return {
    counts: () =>
      Object.fromEntries([...live.entries()].filter(([, set]) => set.size > 0).map(([key, set]) => [key, set.size])),
    restore: () => spies.flat().forEach((spy) => spy.mockRestore()),
  };
}

/** requestAnimationFrame that queues callbacks until flushed; `pending` lists what is still scheduled. */
function trackAnimationFrames() {
  let id = 0;
  const queue = new Map<number, FrameRequestCallback>();
  const request = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
    queue.set(++id, cb);
    return id;
  });
  vi.spyOn(window, 'cancelAnimationFrame').mockImplementation((handle) => {
    queue.delete(handle);
  });
  return {
    request,
    pending: () => queue.size,
    flush(frames = 1) {
      for (let i = 0; i < frames; i++) {
        const batch = [...queue.values()];
        queue.clear();
        batch.forEach((cb) => cb(performance.now()));
      }
    },
  };
}

async function settle() {
  for (let i = 0; i < 4; i++) await act(() => vi.advanceTimersByTimeAsync(0));
}

function pointerMove(target: EventTarget, x: number, y: number) {
  const Ctor = (window.PointerEvent ?? window.MouseEvent) as typeof MouseEvent;
  const event = new Ctor('pointermove', { clientX: x, clientY: y, bubbles: true });
  Object.defineProperty(event, 'pointerType', { value: 'mouse' });
  act(() => {
    target.dispatchEvent(event);
  });
}

// Loads the lazy background chunk once with real timers, so React.lazy resolves within the fake-timer scenarios.
beforeAll(async () => {
  await import('./components/BackgroundLayer');
}, 20000);

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  localStorage.removeItem(PAUSE_KEY);
  resetMotionPauseMemory();
  sessionStorage.clear();
  vi.mocked(authApi.fetchSession).mockResolvedValue(null);
});

afterEach(() => {
  vi.useRealTimers();
  sessionStorage.clear();
  localStorage.removeItem(PAUSE_KEY);
  vi.restoreAllMocks();
});

describe('keyboard order (MAIN_PAGE_FEATURE.md §7)', { timeout: 20000 }, () => {
  it('goes language switch -> login -> password -> password toggle -> submit -> motion toggle', async () => {
    sessionStorage.setItem(INTRO_KEY, '1');
    const media = trackMediaQueries([]);
    try {
      renderAt('/login');
      const submit = await screen.findByRole('button', { name: uk.auth.signIn }, { timeout: 5000 });
      // Submit is enabled (and so focusable) only once both fields are filled and /auth/me has answered.
      fireEvent.change(screen.getByLabelText(/Логін/), { target: { value: 'someone' } });
      fireEvent.change(screen.getByLabelText(/^Пароль/), { target: { value: 'some-pass-1' } });
      await waitFor(() => expect(submit).toBeEnabled(), { timeout: 5000 });

      const expected = [
        within(screen.getByRole('radiogroup', { name: uk.common.language })).getByRole('radio', { checked: true }),
        screen.getByLabelText(/Логін/),
        screen.getByLabelText(/^Пароль/),
        screen.getByRole('button', { name: uk.auth.login.showPassword }),
        submit,
        screen.getByTestId('login-motion-toggle'),
      ];
      (document.activeElement as HTMLElement | null)?.blur();
      const visited: Element[] = [];
      for (let i = 0; i < expected.length; i++) {
        await userEvent.tab();
        visited.push(document.activeElement!);
      }
      expect(visited).toEqual(expected);
      // The motion toggle is the last stop (FT-3): the next Tab leaves the page.
      await userEvent.tab();
      expect(expected).not.toContain(document.activeElement);
      expect(document.activeElement === document.body || !screen.getByTestId('login-shell').contains(document.activeElement)).toBe(true);
    } finally {
      media.restore();
    }
  });
});

describe('no leaks after signing in and navigating (spec §9.13)', { timeout: 30000 }, () => {
  /**
   * Runs `scenario` under fake timers and returns what is alive once it has reached the app's home page, while the
   * app is still mounted (that is when a leak of the sign-in page would keep running).
   */
  async function measure(
    scenario: (frames: ReturnType<typeof trackAnimationFrames>) => Promise<ReturnType<typeof renderAt>>,
  ) {
    vi.useFakeTimers();
    const media = trackMediaQueries([FINE_POINTER_QUERY]);
    const listeners = trackGlobalListeners();
    const frames = trackAnimationFrames();
    let view: ReturnType<typeof renderAt> | undefined;
    try {
      view = await scenario(frames);
      return {
        timers: vi.getTimerCount(),
        frames: frames.pending(),
        listeners: listeners.counts(),
        media: media.attached(),
      };
    } finally {
      view?.unmount();
      listeners.restore();
      media.restore();
    }
  }

  it('leaves no timers, animation frames or listeners of the sign-in page behind', async () => {
    // Control: the same providers and the target page, without ever showing the sign-in page.
    const control = await measure(async () => {
      const view = renderAt('/');
      await settle();
      expect(screen.getByText('home page')).toBeInTheDocument();
      return view;
    });
    vi.useRealTimers();

    vi.mocked(authApi.loginRequest).mockResolvedValue(session());
    const after = await measure(async (frames) => {
      const view = renderAt('/login');
      await settle();
      // Everything that registers something is running: intro, lazy background with particles, parallax, light.
      expect(screen.getByTestId('login-intro')).toBeInTheDocument();
      expect(screen.getByTestId('login-particles')).toBeInTheDocument();
      pointerMove(window, 100, 100);
      pointerMove(screen.getByTestId('login-card').querySelector('section')!, 10, 10);
      expect(frames.request).toHaveBeenCalled();
      frames.flush(3);
      await act(() => vi.advanceTimersByTimeAsync(INTRO_TOTAL_MS));
      expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();

      fireEvent.change(screen.getByLabelText(/Логін/), { target: { value: 'someone' } });
      fireEvent.change(screen.getByLabelText(/^Пароль/), { target: { value: 'some-pass-1' } });
      fireEvent.click(screen.getByRole('button', { name: uk.auth.signIn }));
      await settle();
      expect(screen.getByTestId('login-success-fade')).toBeInTheDocument();
      await act(() => vi.advanceTimersByTimeAsync(loginPageConfig.success.transitionMs));
      await settle();
      expect(screen.getByText('home page')).toBeInTheDocument();
      expect(screen.queryByTestId('login-shell')).not.toBeInTheDocument();
      return view;
    });

    expect(after.timers).toBe(control.timers);
    expect(after.frames).toBe(0);
    expect(after.listeners).toEqual(control.listeners);
    expect(after.media).toBe(control.media);
  });
});
