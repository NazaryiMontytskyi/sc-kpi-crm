import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CurrentUserProvider, STUB_AUTH_STATE, type CurrentUser } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import { renderWithProviders } from '../test/render';
import { HomePage } from '../pages/HomePage';
import * as api from './dashboardApi';
import { isCounterHighlighted, WIDGET_LINKS } from './dashboardLogic';

vi.mock('./dashboardApi', async (orig) => ({ ...(await orig<typeof api>()), getMyDashboard: vi.fn() }));
vi.mock('../knowledge/OnboardingPrompt', () => ({ OnboardingPrompt: () => null }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const user = (permissions: string[]): CurrentUser => ({ id: 'bob', displayName: 'Test', isAdmin: false, permissions });

function renderHome(who: CurrentUser) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={{ ...STUB_AUTH_STATE, user: who }}>
        <HomePage />
      </CurrentUserProvider>
    </QueryClientProvider>,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.getMyDashboard).mockResolvedValue({
    active: 4,
    overdue: 1,
    awaitingAcceptance: 2,
    upcoming: [
      { resolutionId: 'r1', taskId: 't1', taskTitle: 'Фейкова задача', dueDate: '2031-06-01', state: 'IN_PROGRESS', overdue: true },
    ],
  });
});

describe('dashboard logic', () => {
  it('links every counter to a filtered personal list', () => {
    expect(WIDGET_LINKS.active).toBe('/resolutions/mine?filter=ACTIVE');
    expect(WIDGET_LINKS.awaitingAcceptance).toBe('/resolutions/issued?filter=SUBMITTED');
  });

  it('highlights only a non-zero overdue counter', () => {
    expect(isCounterHighlighted('overdue', 1)).toBe(true);
    expect(isCounterHighlighted('overdue', 0)).toBe(false);
    expect(isCounterHighlighted('active', 5)).toBe(false);
  });
});

describe('MyResolutionsWidget', () => {
  it('shows counts, upcoming items and links on the home page', async () => {
    renderHome(user(['dashboard.view', 'tasks.view']));
    await waitFor(() => expect(screen.getByTestId('dashboard-value-active')).toHaveTextContent('4'));
    expect(screen.getByTestId('dashboard-value-overdue')).toHaveTextContent('1');
    expect(screen.getByTestId('dashboard-value-awaitingAcceptance')).toHaveTextContent('2');
    expect(screen.getByTestId('dashboard-counter-active')).toHaveAttribute('href', '/resolutions/mine?filter=ACTIVE');
    expect(screen.getByTestId('dashboard-counter-awaitingAcceptance')).toHaveAttribute(
      'href',
      '/resolutions/issued?filter=SUBMITTED',
    );
    expect(screen.getByText('Фейкова задача')).toHaveAttribute('href', '/tasks/t1');
  });

  it('renders nothing and does not call the API without dashboard.view', () => {
    renderHome(user(['tasks.view']));
    expect(screen.queryByTestId('my-resolutions-widget')).toBeNull();
    expect(api.getMyDashboard).not.toHaveBeenCalled();
  });

  it('shows an empty state without upcoming items', async () => {
    vi.mocked(api.getMyDashboard).mockResolvedValue({ active: 0, overdue: 0, awaitingAcceptance: 0, upcoming: [] });
    renderHome(user(['dashboard.view']));
    expect(await screen.findByText('Немає активних резолюцій.')).toBeInTheDocument();
  });
});
