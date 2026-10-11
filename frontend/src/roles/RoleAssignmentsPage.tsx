import { Alert, Badge, Button, Card, Group, Loader, Select, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { problemMessage } from '../api/problem';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { assignableRoles } from './assignableRoles';
import { useAssignmentMutations, useMemberOptions, useRoles, useUserRoles } from './useRoles';

const TOUCH = 44;
/** Permission that guards granting and revoking roles (the backend is authoritative). */
export const ROLES_ASSIGN = 'roles.assign';

/** User-role assignment panel: pick a member, see their roles, grant or revoke roles. */
export function RoleAssignmentsPage() {
  const { t } = useTranslation();
  usePageTitle('nav.roleAssignments');
  const canAssign = usePermission(ROLES_ASSIGN);
  const [userId, setUserId] = useState<string | null>(null);
  const [roleToAdd, setRoleToAdd] = useState<string | null>(null);
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const members = useMemberOptions();
  const allRoles = useRoles(false);
  const userRoles = useUserRoles(userId);
  const { assign, revoke } = useAssignmentMutations(userId);

  const available = assignableRoles(allRoles.data ?? [], userRoles.data ?? []);
  const loadError = [members, allRoles, userRoles].find((q) => q.isError);

  const onError = (e: unknown) => setErrorKey(problemMessage(e).key);

  return (
    <Stack gap="md">
      <Title order={1} size="h2">
        {t('roleAssignments.title')}
      </Title>
      <Text c="dimmed">{t('roleAssignments.description')}</Text>

      <Select
        label={t('roleAssignments.user')}
        placeholder={t('roleAssignments.userPlaceholder')}
        nothingFoundMessage={t('roleAssignments.userNothing')}
        searchable
        clearable
        data={(members.data ?? []).map((m) => ({ value: m.id, label: m.name }))}
        value={userId}
        onChange={(v) => {
          setUserId(v);
          setRoleToAdd(null);
          setErrorKey(null);
        }}
        disabled={members.isLoading}
        styles={{ input: { minHeight: TOUCH } }}
      />

      {loadError && <Alert color="red">{t(problemMessage(loadError.error).key)}</Alert>}
      {errorKey && <Alert color="red">{t(errorKey)}</Alert>}

      {userId && userRoles.isLoading && <Loader size="sm" />}

      {userId && userRoles.isSuccess && (
        <Stack gap="sm">
          <Text fw={600}>{t('roleAssignments.currentRoles')}</Text>
          {userRoles.data.length === 0 && <Text c="dimmed">{t('roleAssignments.noRoles')}</Text>}
          {userRoles.data.map((r) => (
            <Card key={r.id} withBorder padding="sm" data-testid="user-role">
              <Group justify="space-between" wrap="wrap" align="center">
                <Group gap="xs">
                  <Text fw={500}>{r.name}</Text>
                  {r.system && <Badge variant="light">{t('roles.system')}</Badge>}
                  {r.archived && (
                    <Badge color="gray" variant="light">
                      {t('roleAssignments.archivedInactive')}
                    </Badge>
                  )}
                </Group>
                {canAssign && !r.system && (
                  <Button
                    variant="default"
                    mih={TOUCH}
                    loading={revoke.isPending && revoke.variables === r.id}
                    onClick={() => {
                      setErrorKey(null);
                      revoke.mutate(r.id, { onError });
                    }}
                  >
                    {t('roleAssignments.revoke')}
                  </Button>
                )}
              </Group>
            </Card>
          ))}

          {canAssign && (
            <Group align="flex-end" wrap="wrap">
              <Select
                style={{ flex: '1 1 220px' }}
                label={t('roleAssignments.addRole')}
                placeholder={t('roleAssignments.rolePlaceholder')}
                nothingFoundMessage={t('roleAssignments.roleNothing')}
                data={available.map((r) => ({ value: r.id, label: r.name }))}
                value={roleToAdd}
                onChange={setRoleToAdd}
                styles={{ input: { minHeight: TOUCH } }}
              />
              <Button
                mih={TOUCH}
                disabled={!roleToAdd}
                loading={assign.isPending}
                onClick={() => {
                  if (!roleToAdd) return;
                  setErrorKey(null);
                  assign.mutate(roleToAdd, { onSuccess: () => setRoleToAdd(null), onError });
                }}
              >
                {t('roleAssignments.assign')}
              </Button>
            </Group>
          )}
        </Stack>
      )}
    </Stack>
  );
}
