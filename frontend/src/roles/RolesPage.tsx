import { Alert, Badge, Button, Card, Group, Loader, Modal, Stack, Switch, Table, Text, Title } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { problemMessage } from '../api/problem';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { RoleFormModal } from './RoleFormModal';
import type { Role } from './roleApi';
import { useRoleMutations, useRoles } from './useRoles';

const TOUCH = 44;
/** Permission that guards every role definition change (the backend is authoritative). */
export const ROLES_MANAGE = 'roles.manage';

type Dialog = { kind: 'form'; role: Role | null } | { kind: 'archive'; role: Role } | null;

function StatusBadge({ role }: { role: Role }) {
  const { t } = useTranslation();
  if (role.system) return <Badge variant="light">{t('roles.system')}</Badge>;
  return (
    <Badge color={role.archived ? 'gray' : 'green'} variant="light">
      {role.archived ? t('roles.archived') : t('roles.active')}
    </Badge>
  );
}

function RowActions({ role, onDialog }: { role: Role; onDialog: (d: Dialog) => void }) {
  const { t } = useTranslation();
  return (
    <Group gap="xs" wrap="wrap">
      <Button variant="default" mih={TOUCH} onClick={() => onDialog({ kind: 'form', role })}>
        {role.system || role.archived ? t('roles.view') : t('roles.edit')}
      </Button>
      {!role.system && !role.archived && (
        <Button variant="default" mih={TOUCH} onClick={() => onDialog({ kind: 'archive', role })}>
          {t('roles.archive')}
        </Button>
      )}
    </Group>
  );
}

function ArchiveModal({ role, onClose }: { role: Role; onClose: () => void }) {
  const { t } = useTranslation();
  const { archive } = useRoleMutations();
  const [errorKey, setErrorKey] = useState<string | null>(null);
  return (
    <Modal opened onClose={onClose} centered title={t('roles.archiveTitle')}>
      <Stack gap="md">
        {errorKey && <Alert color="red">{t(errorKey)}</Alert>}
        <Text>{t('roles.archiveConfirm', { name: role.name })}</Text>
        {role.assignedUserCount > 0 && (
          <Alert color="yellow">{t('roles.archiveInUse', { count: role.assignedUserCount })}</Alert>
        )}
        <Group justify="flex-end">
          <Button variant="default" mih={TOUCH} onClick={onClose}>
            {t('roles.cancel')}
          </Button>
          <Button
            color="red"
            mih={TOUCH}
            loading={archive.isPending}
            onClick={() =>
              archive.mutate(role.id, { onSuccess: onClose, onError: (e) => setErrorKey(problemMessage(e).key) })
            }
          >
            {t('roles.archive')}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

/** Role builder: list of roles with create/edit/archive. Mutations need `roles.manage`. */
export function RolesPage() {
  const { t } = useTranslation();
  usePageTitle('nav.roles');
  const canManage = usePermission(ROLES_MANAGE);
  const [showArchived, setShowArchived] = useState(false);
  const [dialog, setDialog] = useState<Dialog>(null);
  const isDesktop = useMediaQuery('(min-width: 48em)');
  const query = useRoles(showArchived);
  const roles = query.data ?? [];
  const loadError = query.isError ? problemMessage(query.error).key : null;

  return (
    <Stack gap="md">
      <Title order={1} size="h2">
        {t('roles.title')}
      </Title>
      <Text c="dimmed">{t('roles.description')}</Text>
      {canManage && (
        <Group justify="space-between" align="center">
          <Switch
            label={t('roles.showArchived')}
            checked={showArchived}
            onChange={(e) => setShowArchived(e.currentTarget.checked)}
            styles={{ root: { minHeight: TOUCH, display: 'flex', alignItems: 'center' } }}
          />
          <Button mih={TOUCH} onClick={() => setDialog({ kind: 'form', role: null })}>
            {t('roles.add')}
          </Button>
        </Group>
      )}

      {query.isLoading && <Loader size="sm" />}
      {loadError && <Alert color="red">{t(loadError === 'problems.UNKNOWN' ? 'roles.loadFailed' : loadError)}</Alert>}

      {!query.isLoading && !loadError && roles.length === 0 && <Text c="dimmed">{t('roles.empty')}</Text>}

      {roles.length > 0 && isDesktop && (
        <Table withTableBorder highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('roles.name')}</Table.Th>
              <Table.Th>{t('roles.descriptionLabel')}</Table.Th>
              <Table.Th>{t('roles.permissionCount')}</Table.Th>
              <Table.Th>{t('roles.assignedUsers')}</Table.Th>
              <Table.Th>{t('roles.status')}</Table.Th>
              {canManage && <Table.Th>{t('roles.actions')}</Table.Th>}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {roles.map((r) => (
              <Table.Tr key={r.id}>
                <Table.Td>{r.name}</Table.Td>
                <Table.Td>{r.description}</Table.Td>
                <Table.Td>{r.permissions.length}</Table.Td>
                <Table.Td>{r.assignedUserCount}</Table.Td>
                <Table.Td>
                  <StatusBadge role={r} />
                </Table.Td>
                {canManage && (
                  <Table.Td>
                    <RowActions role={r} onDialog={setDialog} />
                  </Table.Td>
                )}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {roles.length > 0 && !isDesktop && (
        <Stack gap="sm">
          {roles.map((r) => (
            <Card key={r.id} withBorder padding="md" data-testid="role-card">
              <Stack gap="xs">
                <Group justify="space-between" wrap="nowrap" align="flex-start">
                  <Text fw={600}>{r.name}</Text>
                  <StatusBadge role={r} />
                </Group>
                {r.description && (
                  <Text size="sm" c="dimmed">
                    {r.description}
                  </Text>
                )}
                <Text size="sm" c="dimmed">
                  {t('roles.permissionCount')}: {r.permissions.length} · {t('roles.assignedUsers')}:{' '}
                  {r.assignedUserCount}
                </Text>
                {canManage && <RowActions role={r} onDialog={setDialog} />}
              </Stack>
            </Card>
          ))}
        </Stack>
      )}

      {canManage && dialog?.kind === 'form' && <RoleFormModal role={dialog.role} onClose={() => setDialog(null)} />}
      {canManage && dialog?.kind === 'archive' && <ArchiveModal role={dialog.role} onClose={() => setDialog(null)} />}
    </Stack>
  );
}
