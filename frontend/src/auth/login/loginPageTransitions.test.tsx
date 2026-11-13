import { MantineProvider } from '@mantine/core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, renderHook, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ApiProblemError } from '../../api/problem';
import { changeLanguage } from '../../i18n';
import { renderWithProviders } from '../../test/render';
import { cssVariablesResolver, theme } from '../../theme/theme';
import { AuthProvider } from '../AuthProvider';
import * as authApi from '../authApi';
import { LoginPage } from '../LoginPage';
import { INTRO_TOTAL_MS, IntroSplash } from './components/IntroSplash';
import { introPending } from './hooks/useIntroSplash';
import { resetMotionPauseMemory } from './hooks/useMotionPreference';
import { REDUCED_MOTION_QUERY } from './hooks/usePrefersReducedMotion';
import { useSuccessTransition } from './hooks/useSuccessTransition';
import { loginPageConfig } from './loginPage.config';
import { stubMediaQueries } from './testing/mediaStub';

vi.mock('../authApi', async (orig) => ({
  ...(await orig<typeof authApi>()),
  fetchSession: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(() => Promise.resolve()),
  changePasswordRequest: vi.fn(),
  changeLocaleRequest: vi.fn(),
}));

// The photo background is a lazy decoration with its own tests.
vi.mock('./components/LoginBackground', () => ({ LoginBackground: () => null }));

const INTRO_KEY = loginPageConfig.intro.sessionStorageKey;
const PAUSE_KEY = loginPageConfig.motion.pauseStorageKey;
const { transitionMs } = loginPageConfig.success;

const session = (over: Partial<authApi.Session> = {}): authApi.Session => ({
  user: { id: 'u1', displayName: 'fake.user', isAdmin: false, permissions: [] },
  locale: 'uk',
  mustChangePassword: false,
  ...over,
});

function renderLogin(from?: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver}>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <MemoryRouter initialEntries={[{ pathname: '/login', state: from ? { from } : null }]}>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/" element={<p>home page</p>} />
              <Route path="/tasks" element={<p>tasks page</p>} />
              <Route path="/change-password" element={<p>change password page</p>} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    </MantineProvider>,
  );
}

/** Lets mocked promises and the query/mutation notifications (scheduled with setTimeout 0) run. */
async function settle() {
  await act(() => vi.advanceTimersByTimeAsync(0));
  await act(() => vi.advanceTimersByTimeAsync(0));
}

async function submit() {
  fireEvent.change(screen.getByLabelText(/Логін/), { target: { value: 'someone' } });
  fireEvent.change(screen.getByLabelText(/Пароль/), { target: { value: 'some-pass-1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Увійти' }));
  await settle();
}

/**
 * Fires the end of a CSS animation. jsdom has no AnimationEvent, so React listens for the prefixed name there; both
 * names are dispatched (the page treats repeated ends as one).
 */
function endAnimation(element: Element) {
  act(() => {
    for (const type of ['animationend', 'webkitAnimationEnd']) element.dispatchEvent(new Event(type, { bubbles: true }));
  });
}

/** Records whether the intro splash was ever in the DOM. */
function watchIntro() {
  let seen = false;
  const check = () => {
    if (document.querySelector('[data-testid="login-intro"]')) seen = true;
  };
  const observer = new MutationObserver(check);
  observer.observe(document.body, { subtree: true, childList: true });
  return {
    seen: () => {
      check();
      observer.disconnect();
      return seen;
    },
  };
}

/** Records whether any element ever carried the exiting phase (TR-4: there must be none). */
function watchExitingPhase() {
  let seen = false;
  const check = () => {
    if (document.querySelector('[data-phase="exiting"], [data-testid="login-success-fade"]')) seen = true;
  };
  const observer = new MutationObserver(check);
  observer.observe(document.body, { subtree: true, childList: true, attributes: true });
  return {
    seen: () => {
      check();
      observer.disconnect();
      return seen;
    },
  };
}

let media: ReturnType<typeof stubMediaQueries> | undefined;

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  localStorage.removeItem(PAUSE_KEY);
  resetMotionPauseMemory();
  // The intro has its own tests below; here it would only cover the page.
  sessionStorage.setItem(INTRO_KEY, '1');
  vi.mocked(authApi.fetchSession).mockResolvedValue(null);
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  media?.restore();
  media = undefined;
  sessionStorage.clear();
  localStorage.removeItem(PAUSE_KEY);
  vi.restoreAllMocks();
});

describe('success transition (TR-3)', { timeout: 20000 }, () => {
  it('is configured within 900 ms, with every phase inside it', () => {
    const s = loginPageConfig.success;
    expect(s.transitionMs).toBeLessThanOrEqual(900);
    expect(s.fadeDelayMs + s.fadeMs).toBeLessThanOrEqual(s.transitionMs);
    expect(s.cardMs).toBeLessThanOrEqual(s.transitionMs);
    expect(s.backgroundMs).toBeLessThanOrEqual(s.transitionMs);
  });

  it('plays after a successful sign-in and reaches the target route no later than the configured time', async () => {
    vi.mocked(authApi.loginRequest).mockResolvedValue(session());
    renderLogin();
    await settle();
    await submit();

    expect(screen.getByTestId('login-shell')).toHaveAttribute('data-phase', 'exiting');
    expect(screen.getByTestId('login-success-fade')).toBeInTheDocument();
    expect(screen.queryByText('home page')).not.toBeInTheDocument();

    await act(() => vi.advanceTimersByTimeAsync(transitionMs - 50));
    expect(screen.queryByText('home page')).not.toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(50));
    expect(screen.getByText('home page')).toBeInTheDocument();
    expect(authApi.loginRequest).toHaveBeenCalledTimes(1);
  });

  it('navigates as soon as the full-screen fade ends, only once', async () => {
    vi.mocked(authApi.loginRequest).mockResolvedValue(session());
    renderLogin('/tasks');
    await settle();
    await submit();
    const fade = screen.getByTestId('login-success-fade');
    endAnimation(fade);
    endAnimation(fade);
    await settle();
    expect(screen.getByText('tasks page')).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(transitionMs));
    expect(screen.getByText('tasks page')).toBeInTheDocument();
  });

  it('keeps the frame inert while it plays (no second submit)', async () => {
    vi.mocked(authApi.loginRequest).mockResolvedValue(session());
    renderLogin();
    await settle();
    await submit();
    expect(screen.getByTestId('login-shell')).toHaveAttribute('inert');
  });

  it('honours mustChangePassword after the transition', async () => {
    vi.mocked(authApi.loginRequest).mockResolvedValue(session({ mustChangePassword: true }));
    renderLogin('/tasks');
    await settle();
    await submit();
    await act(() => vi.advanceTimersByTimeAsync(transitionMs));
    expect(screen.getByText('change password page')).toBeInTheDocument();
  });

  it('navigates immediately under reduced motion', async () => {
    media = stubMediaQueries([REDUCED_MOTION_QUERY]);
    vi.mocked(authApi.loginRequest).mockResolvedValue(session());
    const exiting = watchExitingPhase();
    renderLogin();
    await settle();
    await submit();
    expect(screen.getByText('home page')).toBeInTheDocument();
    expect(exiting.seen()).toBe(false);
  });

  it('navigates immediately while the user has paused motion', async () => {
    localStorage.setItem(PAUSE_KEY, 'true');
    vi.mocked(authApi.loginRequest).mockResolvedValue(session());
    renderLogin();
    await settle();
    await submit();
    expect(screen.getByText('home page')).toBeInTheDocument();
  });

  it('never starts after a failed sign-in', async () => {
    vi.mocked(authApi.loginRequest).mockRejectedValue(new ApiProblemError(401, 'INVALID_CREDENTIALS'));
    const exiting = watchExitingPhase();
    renderLogin();
    await settle();
    await submit();
    expect(screen.getByTestId('login-error')).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(transitionMs * 2));
    expect(screen.getByTestId('login-shell')).toHaveAttribute('data-phase', 'idle');
    expect(screen.queryByText('home page')).not.toBeInTheDocument();
    expect(exiting.seen()).toBe(false);
  });

  it('leaves no timer behind when unmounted mid-way (spec §9.13)', async () => {
    vi.mocked(authApi.loginRequest).mockResolvedValue(session());
    const view = renderLogin();
    await settle();
    await submit();
    expect(screen.getByTestId('login-success-fade')).toBeInTheDocument();
    view.unmount();
    await act(() => vi.advanceTimersByTimeAsync(transitionMs * 2));
    expect(screen.queryByText('home page')).not.toBeInTheDocument();

    const hook = renderHook(({ active }) => useSuccessTransition(active), { initialProps: { active: true } });
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    const before = vi.getTimerCount();
    hook.unmount();
    expect(vi.getTimerCount()).toBe(before - 1);
  });
});

describe('already signed in (TR-4)', { timeout: 20000 }, () => {
  it('redirects in the same render as the session arrives, with no exit animation', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue(session());
    const exiting = watchExitingPhase();
    renderLogin();
    await settle();
    expect(screen.getByText('home page')).toBeInTheDocument();
    expect(exiting.seen()).toBe(false);
  });

  it('still honours `from` and mustChangePassword', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue(session());
    const first = renderLogin('/tasks');
    await settle();
    expect(screen.getByText('tasks page')).toBeInTheDocument();
    first.unmount();

    vi.mocked(authApi.fetchSession).mockResolvedValue(session({ mustChangePassword: true }));
    renderLogin('/tasks');
    await settle();
    expect(screen.getByText('change password page')).toBeInTheDocument();
  });

  it('never shows the intro, not even while /auth/me is loading, and keeps the session flag unused', async () => {
    sessionStorage.clear();
    let release!: (value: authApi.Session) => void;
    vi.mocked(authApi.fetchSession).mockReturnValue(new Promise((resolve) => (release = resolve)));
    const intro = watchIntro();
    renderLogin();
    await settle();
    // The bootstrap is still running: nothing is decided yet.
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
    expect(sessionStorage.getItem(INTRO_KEY)).toBeNull();

    release(session());
    await settle();
    expect(screen.getByText('home page')).toBeInTheDocument();
    expect(intro.seen()).toBe(false);
    expect(sessionStorage.getItem(INTRO_KEY)).toBeNull();
  });
});

describe('intro splash (TR-1, TR-2)', { timeout: 20000 }, () => {
  beforeEach(() => sessionStorage.clear());

  it('is configured within 1600 ms', () => {
    const { logoHoldMs, revealMs, logoFadeInMs } = loginPageConfig.intro;
    expect(INTRO_TOTAL_MS).toBe(logoHoldMs + revealMs);
    expect(INTRO_TOTAL_MS).toBeLessThanOrEqual(1600);
    expect(logoFadeInMs).toBeLessThan(logoHoldMs);
  });

  it('shows on the first mount of the session, sets the flag, and not on the next mount (spec §9.10)', async () => {
    const first = renderLogin();
    await settle();
    expect(screen.getByTestId('login-intro')).toBeInTheDocument();
    expect(sessionStorage.getItem(INTRO_KEY)).not.toBeNull();
    first.unmount();

    renderLogin();
    await settle();
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
  });

  it('keeps the form in the DOM and focusable underneath, and goes away after the configured time', async () => {
    renderLogin();
    await settle();
    const intro = screen.getByTestId('login-intro');
    expect(intro).toHaveAttribute('aria-hidden', 'true');
    const login = screen.getByLabelText(/Логін/);
    login.focus();
    expect(login).toHaveFocus();
    expect(screen.getByTestId('login-intro')).toBeInTheDocument();

    await act(() => vi.advanceTimersByTimeAsync(INTRO_TOTAL_MS));
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
  });

  it.each([
    ['a click', () => fireEvent.click(document.body)],
    ['a pointer tap', () => fireEvent.pointerDown(document.body)],
    ['any key', () => fireEvent.keyDown(document.body, { key: 'a' })],
  ])('is dismissed immediately by %s', async (_, dismiss) => {
    renderLogin();
    await settle();
    expect(screen.getByTestId('login-intro')).toBeInTheDocument();
    act(() => dismiss());
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
  });

  it('removes its listeners and timer when it ends', () => {
    const add = vi.spyOn(window, 'addEventListener');
    const remove = vi.spyOn(window, 'removeEventListener');
    const onDone = vi.fn();
    const timersBefore = vi.getTimerCount();
    const view = renderWithProviders(<IntroSplash onDone={onDone} />);
    expect(vi.getTimerCount()).toBe(timersBefore + 1);
    act(() => {
      fireEvent.keyDown(window, { key: 'Escape' });
    });
    expect(onDone).toHaveBeenCalledTimes(1);
    view.unmount();

    const registered = add.mock.calls.filter(([type]) => ['pointerdown', 'click', 'keydown'].includes(type as string));
    expect(registered.map(([type]) => type).sort()).toEqual(['click', 'keydown', 'pointerdown']);
    for (const [type, listener, options] of registered) {
      expect(remove).toHaveBeenCalledWith(type, listener, options);
    }
    expect(vi.getTimerCount()).toBe(timersBefore);
  });

  it('calls onDone once after the configured time without input', () => {
    const onDone = vi.fn();
    renderWithProviders(<IntroSplash onDone={onDone} />);
    act(() => vi.advanceTimersByTime(INTRO_TOTAL_MS - 1));
    expect(onDone).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(1));
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it('waits for the session bootstrap before it is decided, then shows for an anonymous user', async () => {
    let release!: (value: null) => void;
    vi.mocked(authApi.fetchSession).mockReturnValue(new Promise((resolve) => (release = resolve)));
    renderLogin();
    await settle();
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
    expect(sessionStorage.getItem(INTRO_KEY)).toBeNull();
    // The form is there meanwhile.
    expect(screen.getByLabelText(/Логін/)).toBeInTheDocument();

    release(null);
    await settle();
    expect(screen.getByTestId('login-intro')).toBeInTheDocument();
    expect(sessionStorage.getItem(INTRO_KEY)).not.toBeNull();
  });

  it('uses the white logo mark through BrandLogo, without a chip', async () => {
    renderLogin();
    await settle();
    const intro = screen.getByTestId('login-intro');
    const logo = intro.querySelector('img');
    if (logo) {
      expect(logo).toHaveAttribute('data-variant', 'mark-white');
      expect(logo).not.toHaveAttribute('data-on-chip');
    } else {
      expect(within(intro).getByTestId('brand-text')).toBeInTheDocument();
    }
  });

  it('is never rendered under reduced motion (spec §9.8)', async () => {
    media = stubMediaQueries([REDUCED_MOTION_QUERY]);
    renderLogin();
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
    await settle();
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
    expect(sessionStorage.getItem(INTRO_KEY)).toBeNull();
  });

  it('is skipped while the user has paused motion', async () => {
    localStorage.setItem(PAUSE_KEY, 'true');
    renderLogin();
    await settle();
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
  });

  it('disappears at once if reduced motion is switched on while it plays', async () => {
    media = stubMediaQueries([]);
    renderLogin();
    await settle();
    expect(screen.getByTestId('login-intro')).toBeInTheDocument();
    act(() => media!.set(REDUCED_MOTION_QUERY, true));
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
    act(() => media!.set(REDUCED_MOTION_QUERY, false));
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
  });

  it('is skipped, without crashing, when sessionStorage throws (documented choice)', async () => {
    const getItem = Storage.prototype.getItem;
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(function (this: Storage, key: string) {
      if (this === window.sessionStorage) throw new Error('SecurityError');
      return getItem.call(this, key);
    });
    expect(introPending()).toBe(false);
    renderLogin();
    await settle();
    expect(screen.queryByTestId('login-intro')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Увійти' })).toBeInTheDocument();
  });
});
