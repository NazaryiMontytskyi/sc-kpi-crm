import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { changeLanguage } from '../i18n';
import { renderWithProviders } from '../test/render';
import * as api from './searchApi';
import { SearchBar } from './SearchBar';
import { hitLink, visibleGroups } from './searchLogic';
import { SearchPage } from './SearchPage';

vi.mock('./searchApi', async (orig) => ({ ...(await orig<typeof api>()), searchAll: vi.fn() }));
configure({ asyncUtilTimeout: 5000 });

const empty = { total: 0, items: [] };
const result = (over: Partial<api.SearchResult> = {}): api.SearchResult => ({
  query: 'abc',
  tasks: empty,
  resolutions: empty,
  articles: empty,
  people: empty,
  orgUnits: empty,
  ...over,
});
const full = result({
  tasks: { total: 7, items: [{ id: 't1', title: 'Task one', snippet: 'snip' }] },
  resolutions: { total: 1, items: [{ id: 'r1', title: 'Res one', snippet: '', parentId: 't9' }] },
  people: { total: 1, items: [{ id: 'p1', title: 'Fake Person', snippet: '' }] },
});

function Where() {
  const loc = useLocation();
  return <div data-testid="where">{loc.pathname + loc.search}</div>;
}
function renderUi(ui: React.ReactElement, route = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <Routes>
        <Route
          path="*"
          element={
            <>
              {ui}
              <Where />
            </>
          }
        />
      </Routes>
    </QueryClientProvider>,
    route,
  );
}

beforeEach(async () => {
  vi.mocked(api.searchAll).mockReset();
  await changeLanguage('en');
});

describe('searchLogic', () => {
  it('maps hits to routes', () => {
    expect(hitLink('tasks', { id: 'a', title: '', snippet: '' })).toBe('/tasks/a');
    expect(hitLink('resolutions', { id: 'r', title: '', snippet: '', parentId: 't' })).toBe('/tasks/t?tab=resolutions');
    expect(hitLink('articles', { id: 'a', title: '', snippet: '' })).toBe('/knowledge/a');
    expect(hitLink('people', { id: 'p', title: '', snippet: '' })).toBe('/members/p');
    expect(hitLink('orgUnits', { id: 'o', title: '', snippet: '' })).toBe('/org/o');
  });

  it('hides empty groups', () => {
    expect(visibleGroups(full).map((g) => g.key)).toEqual(['tasks', 'resolutions', 'people']);
  });
});

describe('SearchPage', () => {
  it('shows grouped results with counts and links', async () => {
    vi.mocked(api.searchAll).mockResolvedValue(full);
    renderUi(<SearchPage />, '/search?q=abc');
    expect(await screen.findByText('Task one')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('and 6 more')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Articles' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Res one/ })).toHaveAttribute('href', '/tasks/t9?tab=resolutions');
    expect(screen.getByRole('link', { name: /Fake Person/ })).toHaveAttribute('href', '/members/p1');
    expect(api.searchAll).toHaveBeenCalledWith('abc', api.PAGE_LIMIT);
  });

  it('shows the empty state', async () => {
    vi.mocked(api.searchAll).mockResolvedValue(result());
    renderUi(<SearchPage />, '/search?q=abc');
    expect(await screen.findByText('Nothing found for "abc"')).toBeInTheDocument();
  });

  it('shows the error state', async () => {
    vi.mocked(api.searchAll).mockRejectedValue(new Error('boom'));
    renderUi(<SearchPage />, '/search?q=abc');
    expect(await screen.findByText('Search failed. Please try again.')).toBeInTheDocument();
  });

  it('shows a hint and does not query for short input', () => {
    renderUi(<SearchPage />, '/search?q=a');
    expect(screen.getByText('Type at least 2 characters to search')).toBeInTheDocument();
    expect(api.searchAll).not.toHaveBeenCalled();
  });
});

describe('SearchBar', () => {
  it('opens with Ctrl+K, debounces the query and navigates on result click', async () => {
    vi.mocked(api.searchAll).mockResolvedValue(full);
    renderUi(<SearchBar />);
    fireEvent.keyDown(document.body, { key: 'k', ctrlKey: true });
    const input = await screen.findByRole('searchbox', { name: 'Search query' });
    await userEvent.type(input, 'abc');
    expect(api.searchAll).not.toHaveBeenCalledWith('a', expect.anything());
    await userEvent.click(await screen.findByRole('link', { name: /Task one/ }));
    expect(screen.getByTestId('where')).toHaveTextContent('/tasks/t1');
    expect(api.searchAll).toHaveBeenCalledWith('abc', api.PREVIEW_LIMIT);
  });

  it('submitting opens the results page', async () => {
    vi.mocked(api.searchAll).mockResolvedValue(full);
    renderUi(<SearchBar />);
    await userEvent.click(screen.getAllByRole('button', { name: 'Open search' })[0]);
    await userEvent.type(await screen.findByRole('searchbox', { name: 'Search query' }), 'abc{Enter}');
    expect(screen.getByTestId('where')).toHaveTextContent('/search?q=abc');
  });
});
