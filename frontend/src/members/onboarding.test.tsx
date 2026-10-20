import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { changeLanguage } from '../i18n';
import * as orgApi from '../org/orgApi';
import * as roleApi from '../roles/roleApi';
import { renderWithProviders } from '../test/render';
import { OnboardingModal } from './OnboardingModal';
import { buildTasks, remainingTasks, runTasks, type OnboardingTask } from './onboardingPlan';

vi.mock('../org/orgApi', async (orig) => ({
  ...(await orig<typeof orgApi>()),
  getOrgTree: vi.fn(),
  startMembership: vi.fn(),
}));
vi.mock('../roles/roleApi', async (orig) => ({
  ...(await orig<typeof roleApi>()),
  listRoles: vi.fn(),
  assignRole: vi.fn(),
}));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const node = (id: string, name: string): orgApi.OrgNode => ({
  id,
  name,
  type: 'DEPARTMENT',
  description: '',
  archived: false,
  children: [],
});
const role = (id: string, name: string): roleApi.Role => ({
  id,
  name,
  description: '',
  system: false,
  permissions: [],
  archived: false,
  assignedUserCount: 0,
});

describe('onboarding plan', () => {
  const selection = { departments: [{ id: 'd1', name: 'Fake A' }, { id: 'd2', name: 'Fake B' }], role: { id: 'r1', name: 'Fake Role' } };

  it('builds one task per department plus the role', () => {
    const tasks = buildTasks(selection, { departments: true, role: true });
    expect(tasks.map((t) => t.key)).toEqual(['department:d1', 'department:d2', 'role:r1']);
  });

  it('skips steps the user has no permission for', () => {
    expect(buildTasks(selection, { departments: false, role: true }).map((t) => t.kind)).toEqual(['role']);
    expect(buildTasks(selection, { departments: true, role: false }).map((t) => t.kind)).toEqual(['department', 'department']);
    expect(buildTasks(selection, { departments: false, role: false })).toEqual([]);
  });

  it('keeps finished tasks when a later one fails and retries only the rest', async () => {
    const tasks = buildTasks(selection, { departments: true, role: true });
    const calls: string[] = [];
    let failRole = true;
    const execute = async (t: OnboardingTask) => {
      calls.push(t.key);
      if (t.kind === 'role' && failRole) throw new ApiProblemError(403, 'FORBIDDEN');
    };
    const first = await runTasks(tasks, execute, () => 'problems.FORBIDDEN', () => {});
    expect(first.map((t) => t.status)).toEqual(['done', 'done', 'failed']);
    expect(first[2].errorKey).toBe('problems.FORBIDDEN');
    expect(remainingTasks(first).map((t) => t.key)).toEqual(['role:r1']);

    failRole = false;
    calls.length = 0;
    const second = await runTasks(first, execute, () => 'x', () => {});
    expect(calls).toEqual(['role:r1']);
    expect(second.every((t) => t.status === 'done')).toBe(true);
  });
});

describe('OnboardingModal', () => {
  const onFinish = vi.fn();

  function renderModal(canDepartments: boolean, canRole: boolean) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return renderWithProviders(
      <QueryClientProvider client={queryClient}>
        <OnboardingModal memberId="u1" memberName="Новенко Нестор" canDepartments={canDepartments} canRole={canRole} onFinish={onFinish} />
      </QueryClientProvider>,
      '/',
    );
  }

  beforeEach(async () => {
    vi.clearAllMocks();
    await changeLanguage('uk');
    vi.mocked(orgApi.getOrgTree).mockResolvedValue([node('d1', 'Фейковий відділ')]);
    vi.mocked(roleApi.listRoles).mockResolvedValue([role('r1', 'Фейкова роль')]);
    vi.mocked(orgApi.startMembership).mockResolvedValue({
      id: 'ms1',
      userId: 'u1',
      orgUnitId: 'd1',
      since: '2031-09-01',
      until: null,
      active: true,
    });
    vi.mocked(roleApi.assignRole).mockResolvedValue(undefined);
  });

  it('adds to a unit and assigns a role in one flow', async () => {
    renderModal(true, true);
    await userEvent.click(await screen.findByLabelText('Підрозділи', { selector: 'input' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Фейковий відділ', hidden: true }));
    await userEvent.click(screen.getByLabelText('Роль', { selector: 'input' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Фейкова роль', hidden: true }));
    await userEvent.click(screen.getByRole('button', { name: 'Застосувати' }));
    expect(await screen.findByText('Усі кроки виконано.')).toBeInTheDocument();
    expect(orgApi.startMembership).toHaveBeenCalledWith('d1', 'u1', expect.any(String));
    expect(roleApi.assignRole).toHaveBeenCalledWith('u1', 'r1');
  });

  it('disables a step without its permission', async () => {
    renderModal(false, true);
    expect(await screen.findByLabelText('Підрозділи', { selector: 'input' })).toBeDisabled();
    expect(screen.getByText(/потрібне право org\.manage/)).toBeInTheDocument();
    expect(screen.getByLabelText('Роль', { selector: 'input' })).not.toBeDisabled();
  });

  it('shows what remains after a failed step and keeps earlier steps', async () => {
    vi.mocked(roleApi.assignRole).mockRejectedValueOnce(new ApiProblemError(403, 'FORBIDDEN'));
    renderModal(true, true);
    await userEvent.click(await screen.findByLabelText('Підрозділи', { selector: 'input' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Фейковий відділ', hidden: true }));
    await userEvent.click(screen.getByLabelText('Роль', { selector: 'input' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Фейкова роль', hidden: true }));
    await userEvent.click(screen.getByRole('button', { name: 'Застосувати' }));
    expect(await screen.findByText(/Не виконано кроків: 1/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Повторити решту' }));
    expect(await screen.findByText('Усі кроки виконано.')).toBeInTheDocument();
    await waitFor(() => expect(roleApi.assignRole).toHaveBeenCalledTimes(2));
    expect(orgApi.startMembership).toHaveBeenCalledTimes(1);
  });
});
