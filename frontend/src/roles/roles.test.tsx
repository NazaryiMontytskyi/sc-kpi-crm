import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { STUB_AUTH_STATE, CurrentUserProvider, type AuthState } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import { visibleNavRoutes } from '../layout/AppLayout';
import { appRoutes } from '../layout/routes';
import { renderWithProviders } from '../test/render';
import { assignableRoles } from './assignableRoles';
import { PermissionEditor } from './PermissionEditor';
import { groupState, toggleGroup, togglePermission } from './permissionSelection';
import { RoleAssignmentsPage } from './RoleAssignmentsPage';
import * as api from './roleApi';
import { RolesPage } from './RolesPage';
import { validateRole } from './roleValidation';

vi.mock('./roleApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listRoles: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  archiveRole: vi.fn(),
  listPermissionGroups: vi.fn(),
  listUserRoles: vi.fn(),
  assignRole: vi.fn(),
  revokeRole: vi.fn(),
  listMemberOptions: vi.fn(),
}));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const GROUPS: api.PermissionGroup[] = [
  {
    code: 'members',
    label: 'Учасники',
    permissions: [
      { key: 'members.view', label: 'Перегляд учасників' },
      { key: 'members.edit', label: 'Редагування учасників' },
    ],
  },
  { code: 'tasks', label: 'Завдання', permissions: [{ key: 'tasks.view', label: 'Перегляд завдань' }] },
];

const ADMIN: api.Role = {
  id: 'admin',
  name: 'Admin',
  description: '',
  system: true,
  permissions: ['members.view', 'members.edit', 'tasks.view'],
  archived: false,
  assignedUserCount: 1,
};
const EDITORS: api.Role = {
  id: 'r1',
  name: 'Фейкові редактори',
  description: 'Fake role',
  system: false,
  permissions: ['members.view'],
  archived: false,
  assignedUserCount: 2,
};
const VIEWERS: api.Role = { ...EDITORS, id: 'r2', name: 'Фейкові глядачі', assignedUserCount: 0 };

const authWith = (permissions: string[]): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id: 'u', displayName: 'Test User', isAdmin: false, permissions },
});

function renderIn(ui: React.ReactElement, permissions: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={authWith(permissions)}>{ui}</CurrentUserProvider>
    </QueryClientProvider>,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.listRoles).mockResolvedValue([ADMIN, EDITORS, VIEWERS]);
  vi.mocked(api.listPermissionGroups).mockResolvedValue(GROUPS);
  vi.mocked(api.listMemberOptions).mockResolvedValue([{ id: 'm1', name: 'Тестовий Учасник' }]);
  vi.mocked(api.listUserRoles).mockResolvedValue([EDITORS]);
});

describe('permission selection helpers', () => {
  it('toggles single keys keeping a sorted unique list', () => {
    expect(togglePermission(['b'], 'a', true)).toEqual(['a', 'b']);
    expect(togglePermission(['a', 'b'], 'a', true)).toEqual(['a', 'b']);
    expect(togglePermission(['a', 'b'], 'a', false)).toEqual(['b']);
  });
  it('toggles whole groups without touching other groups', () => {
    expect(toggleGroup(['tasks.view'], GROUPS[0], true)).toEqual(['members.edit', 'members.view', 'tasks.view']);
    expect(toggleGroup(['members.view', 'tasks.view'], GROUPS[0], false)).toEqual(['tasks.view']);
  });
  it('reports the group state', () => {
    expect(groupState([], GROUPS[0])).toBe('none');
    expect(groupState(['members.view'], GROUPS[0])).toBe('some');
    expect(groupState(['members.view', 'members.edit'], GROUPS[0])).toBe('all');
  });
});

describe('validateRole', () => {
  it('requires a name and rejects the reserved one', () => {
    expect(validateRole({ name: ' ', description: '' }).name).toBe('roles.errors.nameRequired');
    expect(validateRole({ name: 'ADMIN', description: '' }).name).toBe('problems.ROLE_NAME_RESERVED');
    expect(validateRole({ name: 'x'.repeat(101), description: '' }).name).toBe('roles.errors.nameTooLong');
    expect(validateRole({ name: 'Ok', description: 'd'.repeat(501) }).description).toBe(
      'roles.errors.descriptionTooLong',
    );
    expect(validateRole({ name: 'Ok', description: '' })).toEqual({});
  });
});

describe('assignableRoles', () => {
  it('excludes Admin, archived and already held roles', () => {
    const archived = { ...VIEWERS, id: 'r3', archived: true };
    expect(assignableRoles([ADMIN, EDITORS, VIEWERS, archived], [EDITORS])).toEqual([VIEWERS]);
  });
});

describe('PermissionEditor', () => {
  function Harness({ initial, disabled }: { initial: string[]; disabled?: boolean }) {
    const [value, setValue] = useState(initial);
    return (
      <>
        <PermissionEditor value={value} onChange={setValue} groups={GROUPS} disabled={disabled} />
        <output data-testid="value">{value.join(',')}</output>
      </>
    );
  }

  it('toggles single permissions and whole groups', async () => {
    renderIn(<Harness initial={[]} />, []);
    await userEvent.click(screen.getByLabelText('Перегляд завдань'));
    expect(screen.getByTestId('value')).toHaveTextContent('tasks.view');
    await userEvent.click(screen.getByLabelText('Обрати всі права групи: Учасники'));
    expect(screen.getByTestId('value')).toHaveTextContent('members.edit,members.view,tasks.view');
    await userEvent.click(screen.getByLabelText('Перегляд учасників'));
    expect(screen.getByTestId('value')).toHaveTextContent('members.edit,tasks.view');
    await userEvent.click(screen.getByLabelText('Обрати всі права групи: Учасники'));
    expect(screen.getByTestId('value')).toHaveTextContent('members.edit,members.view,tasks.view');
  });

  it('is read-only when disabled', () => {
    renderIn(<Harness initial={['tasks.view']} disabled />, []);
    expect(screen.getByLabelText('Перегляд завдань')).toBeDisabled();
    expect(screen.getByLabelText('Перегляд завдань')).toBeChecked();
  });
});

describe('RolesPage', () => {
  it('hides mutating actions without roles.manage', async () => {
    renderIn(<RolesPage />, []);
    expect(await screen.findByText('Фейкові редактори')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Створити роль' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Архівувати' })).not.toBeInTheDocument();
  });

  it('shows no archive action for the built-in Admin role', async () => {
    renderIn(<RolesPage />, ['roles.manage']);
    await screen.findByText('Фейкові редактори');
    expect(screen.getAllByRole('button', { name: 'Архівувати' })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Переглянути' })).toHaveLength(1);
  });

  it('creates a role with the chosen permissions', async () => {
    vi.mocked(api.createRole).mockResolvedValue({ ...EDITORS, id: 'new' });
    renderIn(<RolesPage />, ['roles.manage']);
    await userEvent.click(await screen.findByRole('button', { name: 'Створити роль' }));
    await userEvent.type(await screen.findByLabelText(/Назва/), 'Нова фейкова роль');
    await userEvent.click(await screen.findByLabelText('Перегляд завдань'));
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    await waitFor(() =>
      expect(api.createRole).toHaveBeenCalledWith({
        name: 'Нова фейкова роль',
        description: undefined,
        permissions: ['tasks.view'],
      }),
    );
  });

  it('validates the name client-side and shows a duplicate-name problem at the field', async () => {
    vi.mocked(api.createRole).mockRejectedValue(new ApiProblemError(409, 'ROLE_NAME_TAKEN'));
    renderIn(<RolesPage />, ['roles.manage']);
    await userEvent.click(await screen.findByRole('button', { name: 'Створити роль' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText('Вкажіть назву ролі.')).toBeInTheDocument();
    expect(api.createRole).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText(/Назва/), 'Фейкові редактори');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText('Активна роль із такою назвою вже існує.')).toBeInTheDocument();
  });

  it('warns when archiving a role in use and archives on confirm', async () => {
    vi.mocked(api.archiveRole).mockResolvedValue({ ...EDITORS, archived: true });
    renderIn(<RolesPage />, ['roles.manage']);
    await screen.findByText('Фейкові редактори');
    await userEvent.click(screen.getAllByRole('button', { name: 'Архівувати' })[0]);
    expect(await screen.findByText(/Після архівації вони втратять її права/)).toBeInTheDocument();
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Архівувати' }));
    await waitFor(() => expect(api.archiveRole).toHaveBeenCalledWith('r1'));
  });

  it('opens the Admin role read-only', async () => {
    renderIn(<RolesPage />, ['roles.manage']);
    await userEvent.click(await screen.findByRole('button', { name: 'Переглянути' }));
    expect(await screen.findByLabelText('Перегляд учасників')).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Зберегти' })).not.toBeInTheDocument();
  });
});

describe('navigation gating', () => {
  it('lists the pages by their permissions', () => {
    const roles = appRoutes.find((r) => r.path === '/roles')!;
    const assignments = appRoutes.find((r) => r.path === '/role-assignments')!;
    expect(visibleNavRoutes(appRoutes, authWith([]).user)).not.toContain(roles);
    expect(visibleNavRoutes(appRoutes, authWith(['roles.manage']).user)).toContain(roles);
    expect(visibleNavRoutes(appRoutes, authWith(['roles.manage']).user)).not.toContain(assignments);
    expect(visibleNavRoutes(appRoutes, authWith(['roles.assign']).user)).toContain(assignments);
  });
});

describe('RoleAssignmentsPage', () => {
  async function pickUser() {
    await userEvent.click(await screen.findByRole('combobox', { name: 'Учасник' }));
    await userEvent.click(await screen.findByText('Тестовий Учасник'));
  }

  it('shows the roles of the picked member and grants an available role', async () => {
    vi.mocked(api.assignRole).mockResolvedValue();
    renderIn(<RoleAssignmentsPage />, ['roles.assign']);
    await pickUser();
    expect(await screen.findByTestId('user-role')).toHaveTextContent('Фейкові редактори');
    await userEvent.click(screen.getByRole('combobox', { name: 'Додати роль' }));
    await userEvent.click(await screen.findByText('Фейкові глядачі'));
    await userEvent.click(screen.getByRole('button', { name: 'Видати' }));
    await waitFor(() => expect(api.assignRole).toHaveBeenCalledWith('m1', 'r2'));
  });

  it('revokes a role and offers no revoke for the built-in Admin role', async () => {
    vi.mocked(api.revokeRole).mockResolvedValue();
    vi.mocked(api.listUserRoles).mockResolvedValue([ADMIN, EDITORS]);
    renderIn(<RoleAssignmentsPage />, ['roles.assign']);
    await pickUser();
    await screen.findAllByTestId('user-role');
    const revokes = screen.getAllByRole('button', { name: 'Забрати' });
    expect(revokes).toHaveLength(1);
    await userEvent.click(revokes[0]);
    await waitFor(() => expect(api.revokeRole).toHaveBeenCalledWith('m1', 'r1'));
  });

  it('shows no grant or revoke controls without roles.assign', async () => {
    renderIn(<RoleAssignmentsPage />, []);
    await pickUser();
    await screen.findByTestId('user-role');
    expect(screen.queryByRole('button', { name: 'Забрати' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Видати' })).not.toBeInTheDocument();
  });
});
