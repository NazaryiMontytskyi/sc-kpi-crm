import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CurrentUserProvider, STUB_AUTH_STATE, type AuthState } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import { renderWithProviders } from '../test/render';
import * as api from './onboardingApi';
import { OnboardingManagePage } from './OnboardingManagePage';
import { appendUnique, moveItem, readProgress, removeAt, sameIds } from './onboardingLogic';
import { OnboardingPage } from './OnboardingPage';
import { OnboardingPrompt } from './OnboardingPrompt';

vi.mock('./onboardingApi', async (orig) => ({
  ...(await orig<typeof api>()),
  getMyOnboarding: vi.fn(),
  getManagedOnboarding: vi.fn(),
  createOnboardingList: vi.fn(),
  updateOnboardingList: vi.fn(),
  archiveOnboardingList: vi.fn(),
  restoreOnboardingList: vi.fn(),
  setReadMark: vi.fn(),
  getOnboardingPrompt: vi.fn(),
  dismissOnboardingPrompt: vi.fn(),
}));
vi.mock('./knowledgeApi', async (orig) => ({
  ...(await orig<typeof import('./knowledgeApi')>()),
  getArticleTree: vi.fn().mockResolvedValue([]),
  listArticles: vi.fn().mockResolvedValue([
    { id: 'a1', title: 'Welcome', parentArticleId: null, restricted: false, version: 1, tags: [], archived: false, updatedAt: '' },
    { id: 'a2', title: 'Rules', parentArticleId: null, restricted: false, version: 1, tags: [], archived: false, updatedAt: '' },
    { id: 'a3', title: 'Tools', parentArticleId: null, restricted: false, version: 1, tags: [], archived: false, updatedAt: '' },
  ]),
}));
vi.mock('../org/useOrg', () => ({ useOrgTree: () => ({ data: [] }) }));

configure({ asyncUtilTimeout: 5000 });

const item = (articleId: string, title: string, read = false): api.OnboardingItem => ({ articleId, title, restricted: false, read });
const list = (over: Partial<api.OnboardingList> = {}): api.OnboardingList => ({
  id: 'l1',
  title: 'First week',
  orgUnitId: null,
  orgUnitName: null,
  archived: false,
  items: [item('a1', 'Welcome', true), item('a2', 'Rules')],
  ...over,
});

const authWith = (permissions: string[]): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id: 'u1', displayName: 'Test User', isAdmin: false, permissions },
});

function renderRoute(route: string, auth: AuthState) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={auth}>
        <Routes>
          <Route path="/" element={<OnboardingPrompt />} />
          <Route path="/knowledge/onboarding" element={<OnboardingPage />} />
          <Route path="/knowledge/onboarding/manage" element={<OnboardingManagePage />} />
        </Routes>
      </CurrentUserProvider>
    </QueryClientProvider>,
    route,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('en');
});

describe('onboarding list helpers', () => {
  it('moves, appends and removes while keeping order', () => {
    expect(moveItem(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a']);
    expect(moveItem(['a', 'b', 'c'], 2, 1)).toEqual(['a', 'c', 'b']);
    expect(moveItem(['a', 'b'], 0, 5)).toEqual(['a', 'b']);
    expect(appendUnique(['a'], 'b')).toEqual(['a', 'b']);
    expect(appendUnique(['a'], 'a')).toEqual(['a']);
    expect(removeAt(['a', 'b', 'c'], 1)).toEqual(['a', 'c']);
    expect(sameIds(['a', 'b'], ['a', 'b'])).toBe(true);
    expect(sameIds(['a', 'b'], ['b', 'a'])).toBe(false);
  });

  it('counts read items', () => {
    expect(readProgress([item('a', 'A', true), item('b', 'B')])).toEqual({ read: 1, total: 2 });
  });
});

describe('For newcomers page', () => {
  it('shows lists in order with the caller own read marks and toggles one', async () => {
    vi.mocked(api.getMyOnboarding).mockResolvedValue([list(), list({ id: 'l2', title: 'Design team', orgUnitName: 'Design', items: [item('a3', 'Tools')] })]);
    vi.mocked(api.setReadMark).mockResolvedValue(true);
    renderRoute('/knowledge/onboarding', authWith(['kb.view']));

    const lists = await screen.findAllByTestId('onboarding-list');
    expect(lists).toHaveLength(2);
    const first = within(lists[0]);
    expect(first.getByText(/1 of 2 read|1 з 2/)).toBeInTheDocument();
    const links = first.getAllByRole('link');
    expect(links[0]).toHaveTextContent('1. Welcome');
    expect(links[1]).toHaveTextContent('2. Rules');
    expect(first.getByRole('checkbox', { name: /Welcome/ })).toBeChecked();

    await userEvent.click(first.getByRole('checkbox', { name: /Rules/ }));
    await waitFor(() => expect(api.setReadMark).toHaveBeenCalledWith('a2', true));
    expect(screen.queryByRole('link', { name: /Manage newcomer lists/ })).not.toBeInTheDocument();
  });

  it('offers management only with onboarding.manage', async () => {
    vi.mocked(api.getMyOnboarding).mockResolvedValue([]);
    renderRoute('/knowledge/onboarding', authWith(['kb.view', 'onboarding.manage']));
    expect(await screen.findByRole('link', { name: 'Manage newcomer lists' })).toBeInTheDocument();
  });
});

describe('manage page', () => {
  it('reorders and saves the whole order', async () => {
    vi.mocked(api.getManagedOnboarding).mockResolvedValue([list()]);
    vi.mocked(api.updateOnboardingList).mockResolvedValue(list());
    renderRoute('/knowledge/onboarding/manage', authWith(['kb.view', 'onboarding.manage']));

    const editor = await screen.findByTestId('managed-list');
    const rows = () => within(editor).getAllByTestId('onboarding-item').map((r) => r.textContent);
    expect(rows()[0]).toContain('Welcome');
    await userEvent.click(within(within(editor).getAllByTestId('onboarding-item')[0]).getByRole('button', { name: 'Move down' }));
    expect(rows()[0]).toContain('Rules');
    await userEvent.click(within(editor).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(api.updateOnboardingList).toHaveBeenCalledWith('l1', 'First week', ['a2', 'a1']));
  });

  it('shows a no-access notice without the permission and loads nothing', async () => {
    vi.mocked(api.getManagedOnboarding).mockResolvedValue([]);
    renderRoute('/knowledge/onboarding/manage', authWith(['kb.view']));
    expect(await screen.findByText('You do not have permission to manage newcomer lists.')).toBeInTheDocument();
    expect(screen.queryByTestId('managed-list')).not.toBeInTheDocument();
  });
});

describe('newcomer prompt', () => {
  it('appears while pending and content exists, and dismisses once', async () => {
    vi.mocked(api.getOnboardingPrompt).mockResolvedValue(true);
    vi.mocked(api.getMyOnboarding).mockResolvedValue([list()]);
    vi.mocked(api.dismissOnboardingPrompt).mockResolvedValue();
    renderRoute('/', authWith(['kb.view']));

    const prompt = await screen.findByTestId('onboarding-prompt');
    await userEvent.click(within(prompt).getByRole('button', { name: 'Not now' }));
    await waitFor(() => expect(screen.queryByTestId('onboarding-prompt')).not.toBeInTheDocument());
    expect(api.dismissOnboardingPrompt).toHaveBeenCalledTimes(1);
  });

  it('stays hidden when already dismissed or when there is nothing to read', async () => {
    vi.mocked(api.getOnboardingPrompt).mockResolvedValue(false);
    vi.mocked(api.getMyOnboarding).mockResolvedValue([list()]);
    renderRoute('/', authWith(['kb.view']));
    await waitFor(() => expect(api.getOnboardingPrompt).toHaveBeenCalled());
    expect(screen.queryByTestId('onboarding-prompt')).not.toBeInTheDocument();
  });
});
