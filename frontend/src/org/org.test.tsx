import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { STUB_AUTH_STATE, CurrentUserProvider, type AuthState } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import i18n from '../i18n';
import { appRoutes } from '../layout/routes';
import * as memberApi from '../members/memberApi';
import * as roleApi from '../roles/roleApi';
import { renderWithProviders } from '../test/render';
import { isFutureDate, isRealDate, todayKyiv } from './orgDates';
import * as api from './orgApi';
import { OrgPage } from './OrgPage';
import { OrgUnitPage } from './OrgUnitPage';
import { orgErrorKey } from './orgProblems';
import { PositionPage } from './PositionPage';
import { activeUnits, hierarchy, parentOptions, parentRequired, subtreeIds, workingGroups } from './orgTree';
import { validateAppointmentDate, validatePosition, validateUnit } from './orgValidation';

vi.mock('./orgApi', async (orig) => ({
  ...(await orig<typeof api>()),
  getOrgTree: vi.fn(),
  getOrgUnit: vi.fn(),
  listUnitMemberships: vi.fn(),
  listPositions: vi.fn(),
  getPosition: vi.fn(),
  listPositionHistory: vi.fn(),
  archiveOrgUnit: vi.fn(),
  appoint: vi.fn(),
  endAppointment: vi.fn(),
}));
vi.mock('../members/memberApi', async (orig) => ({
  ...(await orig<typeof memberApi>()),
  listMembers: vi.fn(),
  getMember: vi.fn(),
}));
vi.mock('../roles/roleApi', async (orig) => ({ ...(await orig<typeof roleApi>()), listRoles: vi.fn() }));
vi.mock('../audit', () => ({ EntityHistory: () => <div data-testid="history" /> }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const node = (id: string, name: string, type: api.OrgUnitType, children: api.OrgNode[] = [], archived = false): api.OrgNode => ({
  id,
  name,
  type,
  description: '',
  archived,
  children,
});

const WG = node('wg1', 'Фейкова робоча група', 'WORKING_GROUP');
const DIV = node('d1', 'Фейковий відділ', 'DIVISION');
const DEPT = node('dep1', 'Фейковий департамент', 'DEPARTMENT', [DIV, WG]);
const LEAD = node('l1', 'Фейкове керівництво', 'LEADERSHIP', [DEPT]);
const TREE: api.OrgNode[] = [LEAD];

const member = (id: string, lastName: string, firstName: string): memberApi.MemberSummary => ({
  id,
  lastName,
  firstName,
  middleName: '',
  faculty: null,
  studyGroup: '',
  telegram: '',
  joinedAt: '2031-01-01',
  avatarFileId: null,
  archived: false,
});
const ANNA = member('m1', 'Тестенко', 'Анна');
const BOHDAN = member('m2', 'Прикладний', 'Богдан');

const UNIT: api.OrgUnit = {
  id: 'dep1',
  name: DEPT.name,
  type: 'DEPARTMENT',
  parentId: 'l1',
  description: 'Фейковий опис',
  archived: false,
  ancestorIds: ['l1'],
  childIds: ['d1'],
};
const HEAD_POSITION: api.Position = {
  id: 'p1',
  title: 'Фейковий керівник',
  orgUnitId: 'dep1',
  isHead: true,
  capacity: 1,
  defaultRoleIds: ['r1'],
  activeHolderIds: ['m1'],
  vacant: false,
  archived: false,
};
const VACANT_POSITION: api.Position = {
  ...HEAD_POSITION,
  id: 'p2',
  title: 'Фейковий помічник',
  isHead: false,
  capacity: 2,
  defaultRoleIds: [],
  activeHolderIds: [],
  vacant: true,
};
const MEMBERSHIP: api.Membership = {
  id: 'ms1',
  userId: 'm2',
  orgUnitId: 'dep1',
  since: '2031-02-01',
  until: null,
  active: true,
};
const HISTORY: api.Appointment[] = [
  { id: 'a2', positionId: 'p1', userId: 'm1', startDate: '2031-05-01', endDate: null, active: true },
  { id: 'a1', positionId: 'p1', userId: 'm2', startDate: '2030-01-10', endDate: '2031-04-30', active: false },
];

const authWith = (permissions: string[]): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id: 'someone', displayName: 'Test User', isAdmin: false, permissions },
});

function renderIn(ui: React.ReactElement, permissions: string[], route: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={authWith(permissions)}>{ui}</CurrentUserProvider>
    </QueryClientProvider>,
    route,
  );
}

const renderStructure = (permissions: string[]) => renderIn(<OrgPage />, permissions, '/org');
const renderUnit = (permissions: string[]) =>
  renderIn(
    <Routes>
      <Route path="/org/:id" element={<OrgUnitPage />} />
    </Routes>,
    permissions,
    '/org/dep1',
  );
const renderPosition = (permissions: string[]) =>
  renderIn(
    <Routes>
      <Route path="/org/positions/:id" element={<PositionPage />} />
    </Routes>,
    permissions,
    '/org/positions/p1',
  );

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.getOrgTree).mockResolvedValue(TREE);
  vi.mocked(api.getOrgUnit).mockResolvedValue(UNIT);
  vi.mocked(api.listUnitMemberships).mockResolvedValue([MEMBERSHIP]);
  vi.mocked(api.listPositions).mockResolvedValue([HEAD_POSITION, VACANT_POSITION]);
  vi.mocked(api.getPosition).mockResolvedValue(HEAD_POSITION);
  vi.mocked(api.listPositionHistory).mockResolvedValue(HISTORY);
  vi.mocked(memberApi.listMembers).mockResolvedValue([ANNA, BOHDAN]);
  vi.mocked(memberApi.getMember).mockRejectedValue(new ApiProblemError(404, 'NOT_FOUND'));
  vi.mocked(roleApi.listRoles).mockResolvedValue([
    { id: 'r1', name: 'Фейкова роль', description: '', system: false, permissions: [], archived: false, assignedUserCount: 0 },
    { id: 'admin', name: 'Admin', description: '', system: true, permissions: [], archived: false, assignedUserCount: 0 },
  ]);
});

describe('tree helpers', () => {
  it('lists working groups apart from the hierarchy', () => {
    expect(workingGroups(TREE).map((n) => n.id)).toEqual(['wg1']);
    const h = hierarchy(TREE);
    expect(h[0].children[0].children.map((n) => n.id)).toEqual(['d1']);
  });
  it('lists working groups nested under other working groups', () => {
    const inner = node('wg3', 'Вкладена група', 'WORKING_GROUP');
    const outer = node('wg2', 'Зовнішня група', 'WORKING_GROUP', [inner]);
    const tree = [node('l2', 'Керівництво', 'LEADERSHIP', [outer])];
    expect(workingGroups(tree).map((n) => n.id)).toEqual(['wg2', 'wg3']);
    expect(hierarchy(tree)[0].children).toEqual([]);
  });
  it('offers only type-valid parents and never the unit subtree', () => {
    expect(parentOptions(TREE, 'DIVISION').map((p) => p.id)).toEqual(['dep1']);
    expect(parentOptions(TREE, 'DEPARTMENT').map((p) => p.id)).toEqual(['l1']);
    expect(parentOptions(TREE, 'LEADERSHIP')).toEqual([]);
    expect(parentOptions(TREE, 'WORKING_GROUP').map((p) => p.id)).toEqual(['l1', 'dep1', 'd1', 'wg1']);
    expect(parentOptions(TREE, 'WORKING_GROUP', subtreeIds(DEPT)).map((p) => p.id)).toEqual(['l1']);
    expect(parentRequired('DIVISION')).toBe(true);
    expect(parentRequired('DEPARTMENT')).toBe(false);
  });
  it('skips archived units', () => {
    const archived = node('x', 'Архів', 'DEPARTMENT', [], true);
    expect(activeUnits([archived, LEAD]).map((u) => u.id)).toEqual(['l1', 'dep1', 'd1', 'wg1']);
  });
});

describe('dates and validation', () => {
  it('computes the Kyiv calendar date across the midnight boundary', () => {
    // 22:30 UTC is already the next day in Kyiv (UTC+2/+3).
    expect(todayKyiv(new Date('2031-01-10T22:30:00Z'))).toBe('2031-01-11');
    expect(todayKyiv(new Date('2031-01-10T10:00:00Z'))).toBe('2031-01-10');
  });
  it('detects future and impossible dates', () => {
    expect(isFutureDate('2031-01-12', '2031-01-11')).toBe(true);
    expect(isFutureDate('2031-01-11', '2031-01-11')).toBe(false);
    expect(isRealDate('2031-02-30')).toBe(false);
  });
  it('rejects appointment dates in the future or before the start', () => {
    expect(validateAppointmentDate('2031-01-12', '2031-01-11')).toBe('org.validation.dateFuture');
    expect(validateAppointmentDate('2031-01-01', '2031-01-11', '2031-01-05')).toBe('org.validation.dateBeforeStart');
    expect(validateAppointmentDate('', '2031-01-11')).toBe('org.validation.required');
    expect(validateAppointmentDate('2031-01-11', '2031-01-11', '2031-01-05')).toBeNull();
  });
  it('validates units and positions', () => {
    const base = { name: ' ', description: '', type: null, parentId: null };
    expect(validateUnit(base, { withType: true, parentRequired: true })).toEqual({
      name: 'org.validation.required',
      type: 'org.validation.required',
      parentId: 'org.validation.required',
    });
    expect(validateUnit({ ...base, name: 'Фейк', type: 'DEPARTMENT' }, { withType: true, parentRequired: false })).toEqual({});
    expect(validatePosition({ title: 'Фейк', capacity: 0 }).capacity).toBe('org.validation.capacity');
    expect(validatePosition({ title: 'Фейк', capacity: 101 }).capacity).toBe('org.validation.capacity');
    expect(validatePosition({ title: '', capacity: 1 }).title).toBe('org.validation.required');
    expect(validatePosition({ title: 'Фейк', capacity: 3 })).toEqual({});
  });
});

describe('problems', () => {
  it('reads any 403 as a missing permission and maps org codes to localized texts', async () => {
    expect(orgErrorKey(new ApiProblemError(403, undefined))).toBe('problems.FORBIDDEN');
    const key = orgErrorKey(new ApiProblemError(409, 'ORG_UNIT_ARCHIVE_BLOCKED'));
    expect(key).toBe('problems.ORG_UNIT_ARCHIVE_BLOCKED');
    for (const lng of ['uk', 'en']) {
      expect(i18n.exists(key, { lng })).toBe(true);
      expect(i18n.exists('org.unit.archive.blockedHint', { lng })).toBe(true);
    }
  });
});

describe('routes', () => {
  it('registers the structure, the unit page, the position page and a menu entry', () => {
    const paths = appRoutes.map((r) => r.path);
    expect(paths).toEqual(expect.arrayContaining(['/org', '/org/:id', '/org/positions/:id']));
    expect(appRoutes.find((r) => r.path === '/org')?.nav?.labelKey).toBe('nav.org');
  });
});

describe('OrgPage', () => {
  it('shows the hierarchy and lists working groups separately, read-only without permissions', async () => {
    renderStructure([]);
    expect(await screen.findByRole('link', { name: DEPT.name })).toBeInTheDocument();
    const groups = await screen.findByTestId('working-groups');
    expect(within(groups).getByRole('link', { name: WG.name })).toBeInTheDocument();
    // The working group is not part of the tree rows.
    const rows = screen.getAllByTestId('org-node').map((r) => r.textContent ?? '');
    expect(rows.some((r) => r.includes(WG.name))).toBe(false);
    expect(screen.queryByRole('button', { name: 'Нова оргодиниця' })).toBeNull();
  });

  it('collapses a branch with the chevron', async () => {
    renderStructure([]);
    const user = userEvent.setup();
    await screen.findByRole('link', { name: DIV.name });
    await user.click(screen.getByRole('button', { name: `Згорнути «${DEPT.name}»` }));
    await waitFor(() => expect(screen.queryByRole('link', { name: DIV.name })).toBeNull());
  });

  it('offers creating a unit with org.manage', async () => {
    renderStructure(['org.manage']);
    expect(await screen.findByRole('button', { name: 'Нова оргодиниця' })).toBeInTheDocument();
  });
});

describe('OrgUnitPage', () => {
  it('shows the derived head, holder and member names (not ids) and no manage actions to readers', async () => {
    renderUnit([]);
    const head = await screen.findByTestId('unit-head');
    expect(await within(head).findByText('Тестенко Анна')).toBeInTheDocument();
    const cards = await screen.findAllByTestId('position-card');
    expect(cards).toHaveLength(2);
    expect(within(cards[1]).getByText('Вакантна')).toBeInTheDocument();
    const row = await screen.findByTestId('membership-row');
    expect(await within(row).findByText('Прикладний Богдан')).toBeInTheDocument();
    for (const label of ['Нова посада', 'Додати учасника', 'Архівувати', 'Призначити', 'Вилучити', 'Редагувати']) {
      expect(screen.queryByRole('button', { name: label })).toBeNull();
    }
  });

  it('gates actions by their own permission', async () => {
    renderUnit(['positions.appoint']);
    await screen.findAllByTestId('position-card');
    expect(screen.getAllByRole('button', { name: 'Призначити' })).toHaveLength(1); // only the non-full position
    expect(screen.queryByRole('button', { name: 'Нова посада' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Додати учасника' })).toBeNull();
  });

  it('shows all manage actions to a full manager', async () => {
    renderUnit(['org.manage', 'positions.manage']);
    expect(await screen.findByRole('button', { name: 'Нова посада' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Додати учасника' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Перемістити' })).toBeInTheDocument();
    expect(await screen.findAllByRole('button', { name: 'Вилучити' })).toHaveLength(1);
  });

  it('explains a blocked archive', async () => {
    vi.mocked(api.archiveOrgUnit).mockRejectedValue(new ApiProblemError(409, 'ORG_UNIT_ARCHIVE_BLOCKED'));
    renderUnit(['org.manage']);
    const user = userEvent.setup();
    const [open] = await screen.findAllByRole('button', { name: 'Архівувати' });
    await user.click(open);
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Архівувати' }));
    expect(await within(dialog).findByText(/Оргодиницю поки не можна архівувати/)).toBeInTheDocument();
    expect(within(dialog).getByText(/членства в оргодиниці/)).toBeInTheDocument();
  });

  it('shows the backend 403 as a missing permission', async () => {
    vi.mocked(api.archiveOrgUnit).mockRejectedValue(new ApiProblemError(403, undefined));
    renderUnit(['org.manage']);
    const user = userEvent.setup();
    const [open] = await screen.findAllByRole('button', { name: 'Архівувати' });
    await user.click(open);
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Архівувати' }));
    expect(await within(dialog).findByText('У вас немає прав на цю дію.')).toBeInTheDocument();
  });
});

describe('PositionPage', () => {
  it('shows bound roles and the appointment history with names and dates', async () => {
    renderPosition([]);
    const rows = await screen.findAllByTestId('appointment-row');
    expect(rows).toHaveLength(2);
    expect(await within(rows[0]).findByText('Тестенко Анна')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Поточне')).toBeInTheDocument();
    expect(await within(rows[1]).findByText('Прикладний Богдан')).toBeInTheDocument();
    expect(within(rows[1]).getByText('10.01.2030 – 30.04.2031')).toBeInTheDocument();
    expect(await within(screen.getByTestId('position-roles')).findByText('Фейкова роль')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Завершити призначення' })).toBeNull();
  });

  it('lets an appointer end an appointment, with a date that is not in the future', async () => {
    vi.mocked(api.endAppointment).mockResolvedValue({ ...HISTORY[0], active: false, endDate: '2031-06-01' });
    renderPosition(['positions.appoint']);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Завершити призначення' }));
    const dialog = await screen.findByRole('dialog');
    const date = within(dialog).getByLabelText('Дата завершення');
    expect(date).toHaveAttribute('max', todayKyiv());
    await user.clear(date);
    await user.type(date, '2999-01-01');
    await user.click(within(dialog).getByRole('button', { name: 'Завершити призначення' }));
    expect(await within(dialog).findByText('Дата не може бути в майбутньому.')).toBeInTheDocument();
    expect(api.endAppointment).not.toHaveBeenCalled();
  });

  it('blocks appointing with a future start date client-side', async () => {
    vi.mocked(api.getPosition).mockResolvedValue({ ...HEAD_POSITION, activeHolderIds: [], vacant: true });
    renderPosition(['positions.appoint']);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Призначити' }));
    const dialog = await screen.findByRole('dialog');
    const date = within(dialog).getByLabelText('Дата початку');
    expect(date).toHaveAttribute('max', todayKyiv());
    await user.clear(date);
    await user.type(date, '2999-01-01');
    await user.click(within(dialog).getByRole('button', { name: 'Призначити' }));
    expect(await within(dialog).findByText('Дата не може бути в майбутньому.')).toBeInTheDocument();
    expect(within(dialog).getByText('Це поле обов’язкове.')).toBeInTheDocument();
    expect(api.appoint).not.toHaveBeenCalled();
  });
});
