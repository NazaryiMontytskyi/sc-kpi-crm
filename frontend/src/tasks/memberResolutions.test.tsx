import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CurrentUserProvider, STUB_AUTH_STATE, type CurrentUser } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import { appRoutes } from '../layout/routes';
import { MemberResolutionsSection } from '../members/MemberResolutionsSection';
import { renderWithProviders } from '../test/render';
import { isOverdueHighlighted, isStateFilter, STATE_FILTERS, stateFilterLabelKey, toMemberResolutionQuery } from './memberResolutionLogic';
import * as api from './memberResolutionsApi';
import { IssuedByMePage, MyResolutionsPage } from './PersonalResolutionsPage';

vi.mock('./memberResolutionsApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listMemberResolutions: vi.fn(),
  getMemberResolutionCounts: vi.fn(),
}));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const item = (over: Partial<api.MemberResolution> = {}): api.MemberResolution => ({
  id: 'r1',
  taskId: 't1',
  taskTitle: 'Фейкова задача',
  authorId: 'alice',
  executorId: 'bob',
  coExecutorIds: [],
  instruction: 'Підготувати звіт',
  dueDate: '2031-06-01',
  state: 'IN_PROGRESS',
  overdue: false,
  ...over,
});
const page = (items: api.MemberResolution[]): api.MemberResolutionPage => ({ items, page: 0, totalPages: 1, totalElements: items.length });
const user = (permissions: string[] = ['tasks.view']): CurrentUser => ({ id: 'bob', displayName: 'Test', isAdmin: false, permissions });

function renderUi(ui: React.ReactElement, who: CurrentUser = user()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={{ ...STUB_AUTH_STATE, user: who }}>{ui}</CurrentUserProvider>
    </QueryClientProvider>,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.listMemberResolutions).mockResolvedValue(page([]));
  vi.mocked(api.getMemberResolutionCounts).mockResolvedValue({ active: 0, overdue: 0, accepted: 0 });
});

describe('member resolution logic', () => {
  it('maps a state filter and role to the backend query', () => {
    expect(toMemberResolutionQuery('EXECUTOR', 'ACTIVE')).toEqual({ role: 'EXECUTOR', active: true });
    expect(toMemberResolutionQuery('AUTHOR', 'ALL')).toEqual({ role: 'AUTHOR' });
    expect(toMemberResolutionQuery('EXECUTOR', 'ACCEPTED')).toEqual({ role: 'EXECUTOR', state: 'ACCEPTED' });
  });

  it('offers active, all and every resolution state', () => {
    expect(STATE_FILTERS).toHaveLength(9);
    expect(isStateFilter('REWORK')).toBe(true);
    expect(isStateFilter('bogus')).toBe(false);
    expect(isStateFilter(null)).toBe(false);
    expect(stateFilterLabelKey('ACTIVE')).toBe('memberResolutions.filter.ACTIVE');
    expect(stateFilterLabelKey('NEW')).toBe('tasks.resolutions.state.NEW');
  });

  it('highlights the overdue counter only when it is not zero', () => {
    expect(isOverdueHighlighted(0)).toBe(false);
    expect(isOverdueHighlighted(2)).toBe(true);
  });

  it('registers both pages in the navigation behind tasks.view', () => {
    for (const path of ['/resolutions/mine', '/resolutions/issued']) {
      const route = appRoutes.find((r) => r.path === path);
      expect(route?.nav?.permission).toBe('tasks.view');
    }
  });
});

describe('MemberResolutionsSection', () => {
  it('shows counts and the active resolutions with a task link, state and overdue mark', async () => {
    vi.mocked(api.getMemberResolutionCounts).mockResolvedValue({ active: 3, overdue: 1, accepted: 5 });
    vi.mocked(api.listMemberResolutions).mockResolvedValue(page([item({ overdue: true }), item({ id: 'r2', taskId: 't2', taskTitle: 'Друга' })]));
    renderUi(<MemberResolutionsSection userId="carol" />);
    await waitFor(() => expect(screen.getByTestId('count-active')).toHaveTextContent('3'));
    expect(screen.getByTestId('count-overdue')).toHaveTextContent('1');
    expect(screen.getByTestId('count-accepted')).toHaveTextContent('5');
    const cards = await screen.findAllByTestId('member-resolution');
    expect(cards).toHaveLength(2);
    expect(within(cards[0]).getByRole('link', { name: 'Фейкова задача' })).toHaveAttribute('href', '/tasks/t1');
    expect(within(cards[0]).getByText('Прострочено')).toBeInTheDocument();
    expect(within(cards[1]).queryByText('Прострочено')).not.toBeInTheDocument();
    // another member's profile is requested for that member, as executor, active only
    expect(api.listMemberResolutions).toHaveBeenCalledWith('carol', { role: 'EXECUTOR', active: true, page: 0 });
    expect(api.getMemberResolutionCounts).toHaveBeenCalledWith('carol');
  });

  it('shows an empty message when there are no active resolutions', async () => {
    renderUi(<MemberResolutionsSection userId="carol" />);
    expect(await screen.findByText('Активних резолюцій немає.')).toBeInTheDocument();
  });

  it('renders nothing and calls nothing without tasks.view', () => {
    renderUi(<MemberResolutionsSection userId="carol" />, user([]));
    expect(screen.queryByTestId('member-resolutions')).not.toBeInTheDocument();
    expect(api.listMemberResolutions).not.toHaveBeenCalled();
    expect(api.getMemberResolutionCounts).not.toHaveBeenCalled();
  });
});

describe('personal resolution pages', () => {
  it('My resolutions loads my executor list, active by default, and switches the state filter', async () => {
    vi.mocked(api.listMemberResolutions).mockResolvedValue(page([item()]));
    renderUi(<MyResolutionsPage />);
    expect(await screen.findByTestId('member-resolution')).toBeInTheDocument();
    expect(api.listMemberResolutions).toHaveBeenLastCalledWith('bob', { role: 'EXECUTOR', active: true, page: 0 });
    await userEvent.click(screen.getByLabelText('Прийнята'));
    await waitFor(() =>
      expect(api.listMemberResolutions).toHaveBeenLastCalledWith('bob', { role: 'EXECUTOR', state: 'ACCEPTED', page: 0 }),
    );
    await userEvent.click(screen.getByLabelText('Усі'));
    await waitFor(() =>
      expect(api.listMemberResolutions).toHaveBeenLastCalledWith('bob', { role: 'EXECUTOR', page: 0 }),
    );
  });

  it('Issued by me requests the author role', async () => {
    vi.mocked(api.listMemberResolutions).mockResolvedValue(page([item()]));
    renderUi(<IssuedByMePage />);
    expect(await screen.findByTestId('member-resolution')).toBeInTheDocument();
    expect(api.listMemberResolutions).toHaveBeenLastCalledWith('bob', { role: 'AUTHOR', active: true, page: 0 });
    expect(screen.getByRole('heading', { name: 'Видані мною' })).toBeInTheDocument();
  });

  it('shows an error alert when loading fails', async () => {
    vi.mocked(api.listMemberResolutions).mockRejectedValue(new Error('boom'));
    renderUi(<MyResolutionsPage />);
    expect(await screen.findByText('Не вдалося завантажити резолюції.')).toBeInTheDocument();
  });
});
