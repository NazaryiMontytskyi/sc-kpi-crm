import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { ApiProblemError } from '../api/problem';
import { emitSessionEvent } from '../api/sessionEvents';
import { changeLanguage } from '../i18n';
import { renderWithProviders } from '../test/render';
import { AuthProvider } from './AuthProvider';
import * as authApi from './authApi';
import { usePermission } from './CurrentUser';

vi.mock('./authApi', async (orig) => ({
  ...(await orig<typeof authApi>()),
  fetchSession: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(() => Promise.resolve()),
  changePasswordRequest: vi.fn(),
  changeLocaleRequest: vi.fn(),
}));

const session = (over: Partial<authApi.Session> = {}, perms: string[] = []): authApi.Session => ({
  user: { id: 'u1', displayName: 'test.user', isAdmin: false, permissions: perms },
  locale: 'uk',
  mustChangePassword: false,
  ...over,
});

function setup(route = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </QueryClientProvider>,
    route,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
});

describe('sign-in', { timeout: 20000 }, () => {
  beforeEach(() => {
    vi.mocked(authApi.fetchSession).mockResolvedValue(null);
  });

  async function fillAndSubmit() {
    await userEvent.type(await screen.findByLabelText(/Логін/), 'someone');
    await userEvent.type(screen.getByLabelText(/Пароль/), 'some-pass-1');
    await userEvent.click(screen.getByRole('button', { name: 'Увійти' }));
  }

  it('redirects anonymous users to the sign-in page', async () => {
    setup('/');
    expect(await screen.findByRole('button', { name: 'Увійти' })).toBeInTheDocument();
    expect(screen.getByText('Інформаційна система Студентської ради КПІ')).toBeInTheDocument();
  });

  it('shows a localized message for wrong credentials', async () => {
    vi.mocked(authApi.loginRequest).mockRejectedValue(new ApiProblemError(401, 'INVALID_CREDENTIALS'));
    setup('/login');
    await fillAndSubmit();
    expect(await screen.findByTestId('login-error')).toHaveTextContent('Невірний логін або пароль');
  });

  it('shows the lockout message with minutes from Retry-After', async () => {
    vi.mocked(authApi.loginRequest).mockRejectedValue(new ApiProblemError(429, 'LOGIN_LOCKED', 900));
    setup('/login');
    await fillAndSubmit();
    expect(await screen.findByTestId('login-error')).toHaveTextContent('через 15 хв');
  });

  it('signs in, applies the profile locale and opens the app', async () => {
    vi.mocked(authApi.loginRequest).mockResolvedValue(session({ locale: 'en' }));
    setup('/login');
    await fillAndSubmit();
    expect(await screen.findByText('Welcome')).toBeInTheDocument();
  });
});

describe('sign-in racing the initial session bootstrap', { timeout: 20000 }, () => {
  it('keeps submit disabled until /auth/me has answered, then sends the login', async () => {
    let release!: (value: null) => void;
    vi.mocked(authApi.fetchSession).mockReturnValue(new Promise((resolve) => (release = resolve)));
    vi.mocked(authApi.loginRequest).mockResolvedValue(session({ locale: 'en' }));
    setup('/login');

    await userEvent.type(await screen.findByLabelText(/Логін/), 'someone');
    await userEvent.type(screen.getByLabelText(/Пароль/), 'some-pass-1');
    const button = screen.getByRole('button', { name: 'Увійти' });
    expect(button).toBeDisabled();
    await userEvent.click(button);
    expect(authApi.loginRequest).not.toHaveBeenCalled();

    release(null);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Увійти' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Увійти' }));
    expect(await screen.findByText('Welcome')).toBeInTheDocument();
    expect(authApi.loginRequest).toHaveBeenCalledTimes(1);
  });
});

describe('forced password change', { timeout: 20000 }, () => {
  it('keeps a user with a temporary password on the change-password screen', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue(session({ mustChangePassword: true }));
    setup('/');
    expect(await screen.findByRole('heading', { name: 'Зміна пароля' })).toBeInTheDocument();
    expect(screen.queryByText('Ласкаво просимо')).not.toBeInTheDocument();
  });

  it('after a successful change the user reaches the app', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue(session({ mustChangePassword: true }));
    vi.mocked(authApi.changePasswordRequest).mockResolvedValue(session());
    setup('/');
    await userEvent.type(await screen.findByLabelText(/Поточний пароль/), 'temp-pass-1');
    await userEvent.type(screen.getByLabelText(/^Новий пароль/), 'brand-new-pass-1');
    await userEvent.type(screen.getByLabelText(/Повторіть/), 'brand-new-pass-1');
    await userEvent.click(screen.getByRole('button', { name: 'Змінити пароль' }));
    expect(await screen.findByText('Ласкаво просимо')).toBeInTheDocument();
    expect(authApi.changePasswordRequest).toHaveBeenCalledWith('temp-pass-1', 'brand-new-pass-1');
  });

  it('blocks submit on mismatch and shows backend policy errors by code', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue(session({ mustChangePassword: true }));
    vi.mocked(authApi.changePasswordRequest).mockRejectedValue(new ApiProblemError(422, 'PASSWORD_POLICY_VIOLATION'));
    setup('/change-password');
    await userEvent.type(await screen.findByLabelText(/Поточний пароль/), 'temp-pass-1');
    await userEvent.type(screen.getByLabelText(/^Новий пароль/), 'abc');
    await userEvent.type(screen.getByLabelText(/Повторіть/), 'ab');
    expect(screen.getByText('Паролі не збігаються')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Змінити пароль' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/Повторіть/), 'c');
    await userEvent.click(screen.getByRole('button', { name: 'Змінити пароль' }));
    expect(await screen.findByTestId('change-password-error')).toHaveTextContent('Пароль надто слабкий');
  });
});

describe('session handling', () => {
  it('a 401 from any API call sends the user back to sign-in', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue(session());
    setup('/');
    expect(await screen.findByText('Ласкаво просимо')).toBeInTheDocument();
    emitSessionEvent('unauthorized');
    expect(await screen.findByRole('button', { name: 'Увійти' })).toBeInTheDocument();
  });

  it('persists the language switch to the backend', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue(session());
    vi.mocked(authApi.changeLocaleRequest).mockResolvedValue(session({ locale: 'en' }));
    setup('/');
    await userEvent.click(await screen.findByLabelText('Меню користувача'));
    await userEvent.click(await screen.findByText('English'));
    await waitFor(() => expect(authApi.changeLocaleRequest).toHaveBeenCalledWith('en'));
  });
});

describe('usePermission', () => {
  function Probe() {
    return <span>{usePermission('tasks.create') ? 'yes' : 'no'}</span>;
  }
  function probe(perms: string[]) {
    vi.mocked(authApi.fetchSession).mockResolvedValue(session({}, perms));
    return renderWithProviders(
      <QueryClientProvider client={new QueryClient()}>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </QueryClientProvider>,
    );
  }
  it('reflects permissions from /me', async () => {
    probe(['tasks.create']);
    expect(await screen.findByText('yes')).toBeInTheDocument();
  });
  it('is false without the permission', async () => {
    probe([]);
    await waitFor(() => expect(authApi.fetchSession).toHaveBeenCalled());
    expect(await screen.findByText('no')).toBeInTheDocument();
  });
});
