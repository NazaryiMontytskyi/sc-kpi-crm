import { Alert, Badge, Button, Group, Modal, MultiSelect, Select, Stack, Text } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { activeUnits } from '../org/orgTree';
import { todayKyiv } from '../org/orgDates';
import { useOrgMutations, useOrgTree } from '../org/useOrg';
import { assignableRoles } from '../roles/assignableRoles';
import { useAssignmentMutations, useRoles } from '../roles/useRoles';
import { memberErrorKey } from './memberProblems';
import { buildTasks, remainingTasks, runTasks, type OnboardingTask } from './onboardingPlan';

const TOUCH = 44;
export const ONBOARDING_DEPARTMENTS_PERMISSION = 'org.manage';
export const ONBOARDING_ROLE_PERMISSION = 'roles.assign';

interface Props {
  memberId: string;
  memberName: string;
  /** `org.manage`: may add the member to org units. */
  canDepartments: boolean;
  /** `roles.assign`: may grant a role. */
  canRole: boolean;
  /** Leave the wizard (to the member profile). Anything not done stays doable from the profile / org pages. */
  onFinish: () => void;
}

function StatusBadge({ task }: { task: OnboardingTask }) {
  const { t } = useTranslation();
  const color = task.status === 'done' ? 'green' : task.status === 'failed' ? 'red' : 'gray';
  return (
    <Badge color={color} variant="light">
      {t(`members.onboarding.status.${task.status}`)}
    </Badge>
  );
}

/**
 * Post-creation checklist (INC-023, CONTEXT.md 5.1 steps 3-4): profile is already saved; here HR adds the new member
 * to departments and grants a role using the existing org and access APIs. Each step needs its own permission and is
 * disabled without it. Steps run in order; a failed step never rolls back earlier ones, and the checklist keeps showing
 * what remains, with a retry for exactly those items.
 */
export function OnboardingModal({ memberId, memberName, canDepartments, canRole, onFinish }: Props) {
  const { t } = useTranslation();
  const isMobile = useMediaQuery('(max-width: 47.99em)');
  const tree = useOrgTree(false);
  const roles = useRoles(false);
  const orgMutations = useOrgMutations();
  const assignment = useAssignmentMutations(memberId);

  const [unitIds, setUnitIds] = useState<string[]>([]);
  const [roleId, setRoleId] = useState<string | null>(null);
  const [tasks, setTasks] = useState<OnboardingTask[] | null>(null);
  const [running, setRunning] = useState(false);

  const unitOptions = useMemo(
    () => activeUnits(tree.data ?? []).map((u) => ({ value: u.id, label: u.name })),
    [tree.data],
  );
  const roleOptions = useMemo(
    () => assignableRoles(roles.data ?? [], []).map((r) => ({ value: r.id, label: r.name })),
    [roles.data],
  );

  const execute = async (task: OnboardingTask) => {
    if (task.kind === 'department') {
      await orgMutations.startMembership.mutateAsync({ unitId: task.targetId, userId: memberId, since: todayKyiv() });
    } else {
      await assignment.assign.mutateAsync(task.targetId);
    }
  };

  const run = async (list: OnboardingTask[]) => {
    setRunning(true);
    setTasks(list);
    try {
      await runTasks(list, execute, memberErrorKey, setTasks);
    } finally {
      setRunning(false);
    }
  };

  const start = () => {
    const selection = {
      departments: unitIds.map((id) => ({ id, name: unitOptions.find((o) => o.value === id)?.label ?? id })),
      role: roleId ? { id: roleId, name: roleOptions.find((o) => o.value === roleId)?.label ?? roleId } : null,
    };
    const built = buildTasks(selection, { departments: canDepartments, role: canRole });
    if (built.length === 0) {
      onFinish();
      return;
    }
    void run(built);
  };

  const left = tasks ? remainingTasks(tasks) : [];
  const allDone = tasks !== null && left.length === 0;

  return (
    <Modal
      opened
      onClose={onFinish}
      title={t('members.onboarding.title')}
      centered={!isMobile}
      fullScreen={isMobile}
      size="lg"
      closeOnClickOutside={false}
    >
      <Stack gap="md">
        <Text>{t('members.onboarding.intro', { name: memberName })}</Text>
        <Group gap="xs">
          <Badge color="green" variant="light">
            {t('members.onboarding.status.done')}
          </Badge>
          <Text size="sm">{t('members.onboarding.stepProfile')}</Text>
        </Group>

        {tasks === null && (
          <>
            <MultiSelect
              label={t('members.onboarding.departments')}
              placeholder={t('members.onboarding.departmentsPlaceholder')}
              description={canDepartments ? undefined : t('members.onboarding.noPermission', { key: ONBOARDING_DEPARTMENTS_PERMISSION })}
              data={unitOptions}
              value={unitIds}
              onChange={setUnitIds}
              disabled={!canDepartments}
              searchable
              clearable
              nothingFoundMessage={t('members.onboarding.nothingFound')}
              styles={{ input: { minHeight: TOUCH } }}
            />
            <Select
              label={t('members.onboarding.role')}
              placeholder={t('members.onboarding.rolePlaceholder')}
              description={canRole ? undefined : t('members.onboarding.noPermission', { key: ONBOARDING_ROLE_PERMISSION })}
              data={roleOptions}
              value={roleId}
              onChange={setRoleId}
              disabled={!canRole}
              searchable
              clearable
              nothingFoundMessage={t('members.onboarding.nothingFound')}
              styles={{ input: { minHeight: TOUCH } }}
            />
            <Group justify="flex-end">
              <Button variant="default" mih={TOUCH} onClick={onFinish}>
                {t('members.onboarding.skip')}
              </Button>
              <Button mih={TOUCH} onClick={start} disabled={unitIds.length === 0 && !roleId}>
                {t('members.onboarding.apply')}
              </Button>
            </Group>
          </>
        )}

        {tasks !== null && (
          <>
            <Stack gap="xs" component="ul" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {tasks.map((task) => (
                <li key={task.key}>
                  <Group gap="xs" wrap="nowrap" align="flex-start">
                    <StatusBadge task={task} />
                    <Stack gap={0} style={{ minWidth: 0 }}>
                      <Text size="sm" style={{ overflowWrap: 'anywhere' }}>
                        {t(`members.onboarding.task.${task.kind}`, { name: task.label })}
                      </Text>
                      {task.status === 'failed' && task.errorKey && (
                        <Text size="xs" c="red">
                          {t(task.errorKey)}
                        </Text>
                      )}
                    </Stack>
                  </Group>
                </li>
              ))}
            </Stack>
            {allDone ? (
              <Alert color="green">{t('members.onboarding.allDone')}</Alert>
            ) : (
              !running && <Alert color="yellow">{t('members.onboarding.remaining', { count: left.length })}</Alert>
            )}
            <Group justify="flex-end">
              {!allDone && (
                <Button variant="default" mih={TOUCH} loading={running} onClick={() => void run(tasks)}>
                  {t('members.onboarding.retry')}
                </Button>
              )}
              <Button mih={TOUCH} disabled={running} onClick={onFinish}>
                {allDone ? t('members.onboarding.finish') : t('members.onboarding.toProfile')}
              </Button>
            </Group>
          </>
        )}
      </Stack>
    </Modal>
  );
}
