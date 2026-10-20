import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { STUB_AUTH_STATE, CurrentUserProvider, type AuthState } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import * as orgApi from '../org/orgApi';
import type { Appointment, Membership, OrgNode, Position } from '../org/orgApi';
import { renderWithProviders } from '../test/render';
import * as activityApi from './activityLevelApi';
import * as facultyApi from './facultyApi';
import * as api from './memberApi';
import {
  EMPTY_FILTERS,
  activeFilterCount,
  parseFilters,
  toSearchParams,
  withFilter,
  type MemberFilters,
} from './memberFilters';
import { MemberFiltersPanel } from './MemberFiltersPanel';
import * as memberOrgApi from './memberOrgApi';
import { MemberOrgSections } from './MemberOrgSections';
import { indexUnits, splitAppointments, splitMemberships } from './memberProfileOrg';
import { MembersPage } from './MembersPage';

vi.mock('./memberApi', async (orig) => ({ ...(await orig<typeof api>()), searchMembers: vi.fn() }));
vi.mock('./activityLevelApi', async (orig) => ({ ...(await orig<typeof activityApi>()), listActivityLevels: vi.fn() }));
vi.mock('./memberOrgApi', () => ({ listMembershipsOfUser: vi.fn(), listAppointmentsOfUser: vi.fn() }));
vi.mock('./facultyApi', async (orig) => ({ ...(await orig<typeof facultyApi>()), listFaculties: vi.fn() }));
vi.mock('../org/orgApi', async (orig) => ({
  ...(await orig<typeof orgApi>()),
  getOrgTree: vi.fn(),
  listPositions: vi.fn(),
  getPosition: vi.fn(),
}));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const TREE: OrgNode[] = [
  {
    id: 'd1',
    name: 'Фейковий департамент',
    type: 'DEPARTMENT',
    description: '',
    archived: false,
    children: [{ id: 'v1', name: 'Тестовий відділ', type: 'DIVISION', description: '', archived: false, children: [] }],
  },
  { id: 'd0', name: 'Старий департамент', type: 'DEPARTMENT', description: '', archived: true, children: [] },
];
const POSITION: Position = {
  id: 'p1',
  title: 'Фейковий координатор',
  orgUnitId: 'v1',
  isHead: false,
  capacity: 1,
  defaultRoleIds: [],
  activeHolderIds: [],
  vacant: true,
  archived: false,
};

const authWith = (permissions: string[]): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id: 'someone', displayName: 'Test User', isAdmin: false, permissions },
});

function renderIn(ui: React.ReactElement, permissions: string[] = [], route = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={authWith(permissions)}>{ui}</CurrentUserProvider>
    </QueryClientProvider>,
    route,
  );
}

const realMatchMedia = window.matchMedia;
function setDesktop(desktop: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: desktop,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.searchMembers).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  vi.mocked(facultyApi.listFaculties).mockResolvedValue([]);
  vi.mocked(activityApi.listActivityLevels).mockResolvedValue([
    { id: 'l1', code: 'HIGH', labelUk: 'Високий', labelEn: 'High', archived: false },
  ]);
  vi.mocked(orgApi.getOrgTree).mockResolvedValue(TREE);
  vi.mocked(orgApi.listPositions).mockImplementation(async (unitId) => (unitId === 'v1' ? [POSITION] : []));
  vi.mocked(orgApi.getPosition).mockResolvedValue(POSITION);
  vi.mocked(memberOrgApi.listMembershipsOfUser).mockResolvedValue([]);
  vi.mocked(memberOrgApi.listAppointmentsOfUser).mockResolvedValue([]);
});
afterEach(() => {
  window.matchMedia = realMatchMedia;
});

describe('memberFilters URL state', () => {
  it('round-trips every filter through the query string', () => {
    const f: MemberFilters = {
      q: 'анна',
      facultyId: 'f1',
      departmentId: 'd1',
      divisionId: 'v1',
      positionId: 'p1',
      activityLevelId: 'l1',
      status: 'ALL',
      sort: 'joinedAt,desc',
      page: 2,
    };
    expect(parseFilters(new URLSearchParams(toSearchParams(f).toString()))).toEqual(f);
  });
  it('keeps the default view URL clean and parses an empty query to defaults', () => {
    expect(toSearchParams(EMPTY_FILTERS).toString()).toBe('');
    expect(parseFilters(new URLSearchParams(''))).toEqual(EMPTY_FILTERS);
  });
  it('falls back to defaults for malformed values', () => {
    const f = parseFilters(new URLSearchParams('status=BOGUS&sort=x,y&page=-3&faculty=%20'));
    expect(f.status).toBe('ACTIVE');
    expect(f.sort).toBe(EMPTY_FILTERS.sort);
    expect(f.page).toBe(0);
    expect(f.facultyId).toBeNull();
  });
  it('counts narrowing filters only and resets the page on change', () => {
    expect(activeFilterCount({ ...EMPTY_FILTERS, q: 'x', page: 3, sort: 'joinedAt,asc' })).toBe(0);
    expect(activeFilterCount({ ...EMPTY_FILTERS, facultyId: 'f', positionId: 'p', status: 'ARCHIVED' })).toBe(3);
    expect(withFilter({ ...EMPTY_FILTERS, page: 4 }, { departmentId: 'd1' }).page).toBe(0);
  });
});

describe('MemberFiltersPanel', () => {
  it('offers the status control only to members.archive holders and reports changes', async () => {
    const onChange = vi.fn();
    const { unmount } = renderIn(
      <MemberFiltersPanel filters={{ ...EMPTY_FILTERS, page: 3 }} onChange={onChange} canSeeArchived />,
    );
    await userEvent.click(await screen.findByText('Архівовані'));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ status: 'ARCHIVED', page: 0 }));
    unmount();
    renderIn(<MemberFiltersPanel filters={EMPTY_FILTERS} onChange={onChange} canSeeArchived={false} />);
    await screen.findByTestId('member-filters');
    expect(screen.queryByText('Архівовані')).not.toBeInTheDocument();
  });
});

describe('MemberFiltersPanel activity level', () => {
  it('offers the configured levels and reports the chosen one', async () => {
    const onChange = vi.fn();
    renderIn(<MemberFiltersPanel filters={{ ...EMPTY_FILTERS, page: 2 }} onChange={onChange} canSeeArchived={false} />);
    await userEvent.click((await screen.findAllByLabelText('Рівень активності'))[0]);
    await userEvent.click(await screen.findByRole('option', { name: 'Високий', hidden: true }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ activityLevelId: 'l1', page: 0 }));
  });
});

describe('MembersPage filters', () => {
  it('shows the active filter count on the mobile button and resets filters from the drawer', async () => {
    renderIn(<MembersPage />, ['members.archive'], '/?department=d1&position=p1');
    const open = await screen.findByRole('button', { name: 'Фільтри (2)' });
    await waitFor(() =>
      expect(api.searchMembers).toHaveBeenCalledWith(expect.objectContaining({ departmentId: 'd1', positionId: 'p1' })),
    );
    expect(screen.queryByTestId('member-filters')).not.toBeInTheDocument();
    await userEvent.click(open);
    expect(await screen.findByTestId('member-filters')).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Скинути фільтри' }));
    await waitFor(() =>
      expect(api.searchMembers).toHaveBeenLastCalledWith(expect.objectContaining({ departmentId: null, positionId: null })),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Показати результати' }));
    expect(await screen.findByRole('button', { name: 'Фільтри' })).toBeInTheDocument();
  });

  it('renders the filters inline without a drawer button on desktop', async () => {
    setDesktop(true);
    renderIn(<MembersPage />);
    expect(await screen.findByTestId('member-filters')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Фільтри/ })).not.toBeInTheDocument();
  });

  it('forces ACTIVE for users without members.archive even if the URL asks for archived', async () => {
    renderIn(<MembersPage />, [], '/?status=ALL');
    await waitFor(() => expect(api.searchMembers).toHaveBeenCalledWith(expect.objectContaining({ status: 'ACTIVE' })));
  });
});

describe('memberProfileOrg', () => {
  const m = (id: string, since: string, until: string | null): Membership => ({
    id,
    userId: 'u',
    orgUnitId: 'd1',
    since,
    until,
    active: until === null,
  });
  it('splits memberships and appointments into current and past, newest first', () => {
    const s = splitMemberships([m('a', '2030-01-01', '2030-06-01'), m('b', '2031-01-01', null), m('c', '2029-01-01', '2029-12-01')]);
    expect(s.current.map((x) => x.id)).toEqual(['b']);
    expect(s.past.map((x) => x.id)).toEqual(['a', 'c']);
    const a: Appointment = { id: 'x', positionId: 'p1', userId: 'u', startDate: '2031-01-01', endDate: null, active: true };
    expect(splitAppointments([a]).current).toHaveLength(1);
  });
  it('indexes unit names including nested and archived units', () => {
    const { names } = indexUnits(TREE);
    expect(names.get('v1')).toBe('Тестовий відділ');
    expect(names.get('d0')).toBe('Старий департамент');
  });
});

describe('MemberOrgSections', () => {
  it('shows current and past positions and memberships plus the placeholders', async () => {
    vi.mocked(memberOrgApi.listAppointmentsOfUser).mockResolvedValue([
      { id: 'a1', positionId: 'p1', userId: 'u', startDate: '2031-01-01', endDate: null, active: true },
      { id: 'a2', positionId: 'p1', userId: 'u', startDate: '2029-01-01', endDate: '2030-01-01', active: false },
    ]);
    vi.mocked(memberOrgApi.listMembershipsOfUser).mockResolvedValue([
      { id: 'm1', userId: 'u', orgUnitId: 'd1', since: '2031-01-01', until: null, active: true },
      { id: 'm2', userId: 'u', orgUnitId: 'd0', since: '2029-01-01', until: '2030-01-01', active: false },
    ]);
    renderIn(<MemberOrgSections userId="u" />);
    const positions = await screen.findByTestId('profile-positions');
    await waitFor(() => expect(positions.textContent).toContain('Фейковий координатор — Тестовий відділ'));
    expect(positions.textContent).toContain('Поточні');
    expect(positions.textContent).toContain('Минулі');
    expect(positions.textContent).toContain('01.01.2031 — донині');
    const memberships = screen.getByTestId('profile-memberships');
    expect(memberships.textContent).toContain('Фейковий департамент');
    expect(memberships.textContent).toContain('Старий департамент');
    expect(memberships.textContent).toContain('01.01.2029 — 01.01.2030');
    for (const id of ['profile-tasks', 'profile-resolutions', 'profile-statistics']) {
      expect(screen.getByTestId(id)).toBeInTheDocument();
    }
  });

  it('shows empty states when the person has no positions or memberships', async () => {
    renderIn(<MemberOrgSections userId="u" />);
    expect(await screen.findByText('Посад немає.')).toBeInTheDocument();
    expect(screen.getByText('Членства немає.')).toBeInTheDocument();
  });
});
