import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { CurrentUserProvider, STUB_AUTH_STATE } from './auth/CurrentUser';
import { changeLanguage } from './i18n';
import { renderWithProviders } from './test/render';

describe('App shell', () => {
  it('renders home in uk, shell chrome, title, and switches language to en', async () => {
    await changeLanguage('uk');
    renderWithProviders(
      <QueryClientProvider client={new QueryClient()}>
      <CurrentUserProvider value={STUB_AUTH_STATE}>
        <App />
      </CurrentUserProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText('Ласкаво просимо')).toBeInTheDocument();
    expect(screen.getAllByText('SC KPI TMS').length).toBeGreaterThan(0);
    expect(screen.getByTestId('bell-slot')).toBeInTheDocument();
    expect(document.title).toBe('Головна · ІС СР КПІ');

    await userEvent.click(screen.getByLabelText('Меню користувача'));
    await userEvent.click(await screen.findByText('English'));
    expect(await screen.findByText('Welcome')).toBeInTheDocument();
    expect(document.title).toBe('Home · SC KPI IS');
    await changeLanguage('uk');
  });

  it('shows 404 with a link home for unknown paths', async () => {
    await changeLanguage('uk');
    renderWithProviders(
      <QueryClientProvider client={new QueryClient()}>
        <CurrentUserProvider value={STUB_AUTH_STATE}>
          <App />
        </CurrentUserProvider>
      </QueryClientProvider>,
      '/nope',
    );
    expect(screen.getByText('Сторінку не знайдено')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'На головну' })).toHaveAttribute('href', '/');
  });
});
