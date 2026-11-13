import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../../App';
import { changeLanguage } from '../../../i18n';
import { renderWithProviders } from '../../../test/render';
import { AuthProvider } from '../../AuthProvider';
import * as authApi from '../../authApi';
import type * as LoginBackgroundModule from './LoginBackground';

vi.mock('../../authApi', async (orig) => ({
  ...(await orig<typeof authApi>()),
  fetchSession: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(() => Promise.resolve()),
  changePasswordRequest: vi.fn(),
  changeLocaleRequest: vi.fn(),
}));

// The background chunk never arrives: the form must not wait for it (INC-057 AC 11).
const pendingChunk = vi.hoisted(() => vi.fn(() => new Promise<never>(() => {})));
vi.mock('./LoginBackground', async (orig) => {
  const actual = await orig<typeof LoginBackgroundModule>();
  return { ...actual, LoginBackground: actual.createLazyBackground(pendingChunk) };
});

function setup(route: string) {
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

describe('sign-in background loading', { timeout: 20000 }, () => {
  it('renders a usable form while the background chunk is still pending', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue(null);
    vi.mocked(authApi.loginRequest).mockReturnValue(new Promise(() => {}));
    setup('/login');

    await userEvent.type(await screen.findByLabelText(/Логін/), 'fake.user');
    await userEvent.type(screen.getByLabelText(/Пароль/), 'fake-pass-1');
    await userEvent.click(screen.getByRole('button', { name: 'Увійти' }));

    expect(authApi.loginRequest).toHaveBeenCalledWith('fake.user', 'fake-pass-1');
    expect(pendingChunk).toHaveBeenCalled();
    // A static dark placeholder stands in for the background, so there is no white flash.
    expect(screen.getByTestId('login-background-placeholder')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.queryByTestId('login-background')).not.toBeInTheDocument();
  });

  it('does not render the background on the change-password screen', async () => {
    vi.mocked(authApi.fetchSession).mockResolvedValue({
      user: { id: 'u1', displayName: 'test.user', isAdmin: false, permissions: [] },
      locale: 'uk',
      mustChangePassword: true,
    });
    setup('/');
    expect(await screen.findByRole('heading', { name: 'Зміна пароля' })).toBeInTheDocument();
    expect(screen.queryByTestId('login-background-placeholder')).not.toBeInTheDocument();
    expect(screen.queryByTestId('login-background')).not.toBeInTheDocument();
    expect(pendingChunk).not.toHaveBeenCalled();
  });
});
