import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../../../api/problem';
import { changeLanguage } from '../../../i18n';
import en from '../../../i18n/locales/en.json';
import uk from '../../../i18n/locales/uk.json';
import { renderWithProviders } from '../../../test/render';
import { AuthProvider } from '../../AuthProvider';
import * as authApi from '../../authApi';
import { LoginPage } from '../../LoginPage';
import { REDUCED_MOTION_QUERY } from '../hooks/usePrefersReducedMotion';
import type { LoginFormState } from '../hooks/useLoginForm';
import { loginPageConfig } from '../loginPage.config';
import { LoginAlternativesSlot } from './LoginAlternativesSlot';
import { LOGIN_CARD_STYLE, LoginCard } from './LoginCard';

vi.mock('../../authApi', async (orig) => ({
  ...(await orig<typeof authApi>()),
  fetchSession: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(() => Promise.resolve()),
}));

// The photo background is a lazy decoration; it is covered by its own tests.
vi.mock('./LoginBackground', () => ({ LoginBackground: () => null }));

const session: authApi.Session = {
  user: { id: 'u1', displayName: 'fake.user', isAdmin: false, permissions: [] },
  locale: 'uk',
  mustChangePassword: false,
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </QueryClientProvider>,
    '/login',
  );
}

/** A static form state for rendering the card in isolation. */
function fakeForm(over: Partial<LoginFormState> = {}): LoginFormState {
  return {
    status: 'anonymous',
    mustChangePassword: false,
    login: '',
    setLogin: vi.fn(),
    password: '',
    setPassword: vi.fn(),
    submit: vi.fn((e) => e.preventDefault()),
    problem: null,
    pending: false,
    disabled: true,
    failureCount: 0,
    ...over,
  };
}

/** matchMedia reporting the given reduced-motion preference (and `false` for every other query). */
function stubReducedMotion(reduce: boolean) {
  const original = window.matchMedia;
  window.matchMedia = vi.fn((query: string) => ({
    matches: query === REDUCED_MOTION_QUERY && reduce,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
  return () => (window.matchMedia = original);
}

const loginField = () => screen.getByLabelText(/Логін/);
const passwordField = () => screen.getByLabelText(/Пароль/);
const submitButton = () => screen.getByRole('button', { name: 'Увійти' });

async function fill() {
  await userEvent.type(await screen.findByLabelText(/Логін/), 'someone');
  await userEvent.type(passwordField(), 'some-pass-1');
  await waitFor(() => expect(submitButton()).toBeEnabled());
}

beforeEach(async () => {
  vi.clearAllMocks();
  vi.mocked(authApi.fetchSession).mockResolvedValue(null);
  await changeLanguage('uk');
});

describe('LoginCard fields (CARD-3, CARD-5)', { timeout: 20000 }, () => {
  it('keeps real labels and the autocomplete hints; marks filled fields for the floating label', async () => {
    renderPage();
    const login = await screen.findByLabelText(/Логін/);
    expect(login.tagName).toBe('INPUT');
    expect(login).toHaveAttribute('autocomplete', 'username');
    expect(passwordField()).toHaveAttribute('autocomplete', 'current-password');
    expect(document.querySelector(`label[for="${login.id}"]`)).not.toBeNull();

    const field = login.parentElement as HTMLElement;
    expect(field).not.toHaveAttribute('data-filled');
    await userEvent.type(login, 'x');
    expect(field).toHaveAttribute('data-filled');
  });

  it('toggles the password visibility with a state-dependent accessible name in uk and en', async () => {
    renderPage();
    await screen.findByLabelText(/Логін/);
    expect(passwordField()).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: uk.auth.login.showPassword }));
    expect(passwordField()).toHaveAttribute('type', 'text');
    const hide = screen.getByRole('button', { name: uk.auth.login.hidePassword });
    expect(hide).toHaveAttribute('type', 'button');

    await act(() => changeLanguage('en'));
    expect(screen.getByRole('button', { name: en.auth.login.hidePassword })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: en.auth.login.hidePassword }));
    expect(screen.getByLabelText(/^Password/)).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: en.auth.login.showPassword })).toBeInTheDocument();
    await act(() => changeLanguage('uk'));
  });

  it('shows the Caps Lock hint only while Caps Lock is on and links it to the password field', async () => {
    renderPage();
    await screen.findByLabelText(/Логін/);
    const password = passwordField();
    expect(screen.queryByTestId('login-caps-lock')).toBeNull();
    await userEvent.click(password);

    const capsOn = new KeyboardEvent('keydown', { key: 'A', bubbles: true, modifierCapsLock: true } as KeyboardEventInit);
    act(() => void password.dispatchEvent(capsOn));
    const hint = screen.getByTestId('login-caps-lock');
    expect(hint).toHaveTextContent(uk.auth.login.capsLock);
    expect(password.getAttribute('aria-describedby')).toContain(hint.id);
    expect(hint.parentElement).toHaveAttribute('aria-live', 'polite');

    // Non-blocking: the toggle next to it still works and keeps the hint.
    await userEvent.click(screen.getByRole('button', { name: uk.auth.login.showPassword }));
    expect(screen.getByTestId('login-caps-lock')).toBeInTheDocument();

    const capsOff = new KeyboardEvent('keyup', { key: 'a', bubbles: true, modifierCapsLock: false } as KeyboardEventInit);
    act(() => void password.dispatchEvent(capsOff));
    expect(screen.queryByTestId('login-caps-lock')).toBeNull();
    expect(password).not.toHaveAttribute('aria-describedby');

    // Hidden again once focus leaves the field.
    await userEvent.click(password);
    act(() => void password.dispatchEvent(capsOn));
    expect(screen.getByTestId('login-caps-lock')).toBeInTheDocument();
    await userEvent.click(loginField());
    expect(screen.queryByTestId('login-caps-lock')).toBeNull();
  });

  it('has the Caps Lock hint in both languages', () => {
    expect(uk.auth.login.capsLock).toMatch(/Caps Lock/);
    expect(en.auth.login.capsLock).toMatch(/Caps Lock/);
    expect(uk.auth.login.capsLock).not.toBe(en.auth.login.capsLock);
  });
});

describe('LoginCard submit and errors (CARD-6, CARD-7, spec §7, §9.5)', { timeout: 20000 }, () => {
  it.each([
    ['login', loginField],
    ['password', passwordField],
  ])('submits exactly once on Enter in the %s field, with a spinner while pending', async (_, field) => {
    let resolve!: (s: authApi.Session) => void;
    vi.mocked(authApi.loginRequest).mockReturnValue(new Promise((r) => (resolve = r)));
    renderPage();
    await fill();
    await userEvent.type(field(), '{Enter}');
    await userEvent.type(field(), '{Enter}');
    expect(authApi.loginRequest).toHaveBeenCalledTimes(1);
    expect(authApi.loginRequest).toHaveBeenCalledWith('someone', 'some-pass-1');
    const button = submitButton();
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('data-loading');
    await act(async () => resolve(session));
  });

  it('shakes the card on each failure, keeps the form usable and links the error to the fields', async () => {
    vi.mocked(authApi.loginRequest).mockRejectedValue(new ApiProblemError(401, 'INVALID_CREDENTIALS'));
    renderPage();
    await fill();
    const card = screen.getByTestId('login-card');
    expect(card).not.toHaveAttribute('data-shake');
    expect(loginField()).not.toHaveAttribute('aria-describedby');

    await userEvent.click(submitButton());
    const error = await screen.findByTestId('login-error');
    expect(error).toHaveAttribute('role', 'alert');
    expect(error).toHaveTextContent('Невірний логін або пароль');
    expect(error.parentElement).toHaveAttribute('aria-live', 'polite');
    expect(loginField()).toHaveAttribute('aria-describedby', error.id);
    expect(passwordField().getAttribute('aria-describedby')).toContain(error.id);
    expect(card).toHaveAttribute('data-shake', 'on');
    const firstShake = card.className;

    expect(submitButton()).toBeEnabled();
    await userEvent.click(submitButton());
    await waitFor(() => expect(authApi.loginRequest).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(card.className).not.toBe(firstShake));
    expect(card).toHaveAttribute('data-shake', 'on');
    expect(submitButton()).toBeEnabled();
    expect(loginField()).toBeEnabled();
  });

  it('never shakes under prefers-reduced-motion', async () => {
    const restore = stubReducedMotion(true);
    try {
      vi.mocked(authApi.loginRequest).mockRejectedValue(new ApiProblemError(401, 'INVALID_CREDENTIALS'));
      renderPage();
      await fill();
      const firstClass = screen.getByTestId('login-card').className;
      await userEvent.click(submitButton());
      expect(await screen.findByTestId('login-error')).toBeInTheDocument();
      expect(screen.getByTestId('login-card')).not.toHaveAttribute('data-shake');
      expect(screen.getByTestId('login-card').className).toBe(firstClass);
    } finally {
      restore();
    }
  });

  it('never shakes and drops the entrance/stagger while the user has paused motion (FT-3, §6.3)', async () => {
    localStorage.setItem(loginPageConfig.motion.pauseStorageKey, 'true');
    try {
      vi.mocked(authApi.loginRequest).mockRejectedValue(new ApiProblemError(401, 'INVALID_CREDENTIALS'));
      renderPage();
      await fill();
      const card = screen.getByTestId('login-card');
      expect(card).toHaveAttribute('data-motion', 'off');
      const firstClass = card.className;
      await userEvent.click(submitButton());
      expect(await screen.findByTestId('login-error')).toBeInTheDocument();
      expect(card).not.toHaveAttribute('data-shake');
      expect(card.className).toBe(firstClass);
    } finally {
      localStorage.removeItem(loginPageConfig.motion.pauseStorageKey);
    }
  });

  it('keeps card motion on by default', () => {
    renderWithProviders(<LoginCard form={fakeForm()} />);
    expect(screen.getByTestId('login-card')).toHaveAttribute('data-motion', 'on');
  });

  it('keeps the shake within 400 ms and passes the configured timings to the CSS', () => {
    expect(loginPageConfig.card.shakeMs).toBeLessThanOrEqual(400);
    const style = LOGIN_CARD_STYLE as Record<string, string>;
    expect(style['--login-shake']).toBe(`${loginPageConfig.card.shakeMs}ms`);
    expect(style['--login-card-rise']).toBe('24px');
    renderWithProviders(<LoginCard form={fakeForm()} />);
    expect(screen.getByTestId('login-card').style.getPropertyValue('--login-shake')).toBe(
      `${loginPageConfig.card.shakeMs}ms`,
    );
  });
});

describe('LoginAlternativesSlot (CARD-9)', () => {
  it('renders nothing today', () => {
    expect(LoginAlternativesSlot({})).toBeNull();
    const { container } = render(<LoginAlternativesSlot />);
    expect(container).toBeEmptyDOMElement();
  });

  it('does not change the card layout classes when filled later', () => {
    const empty = renderWithProviders(<LoginCard form={fakeForm()} />);
    const classesOf = () =>
      [screen.getByTestId('login-card'), ...screen.getByTestId('login-card').querySelectorAll('section, form')].map(
        (el) => el.className,
      );
    const before = classesOf();
    expect(screen.queryByTestId('login-alternatives')).toBeNull();
    empty.unmount();

    renderWithProviders(<LoginCard form={fakeForm()} alternatives={<button type="button">KPI ID</button>} />);
    expect(classesOf()).toEqual(before);
    const slot = screen.getByTestId('login-alternatives');
    expect(slot.previousElementSibling).toContainElement(screen.getByRole('button', { name: 'Увійти' }));
  });
});

afterEach(() => {
  vi.mocked(authApi.loginRequest).mockReset();
});
