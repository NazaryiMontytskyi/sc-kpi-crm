import { Alert, Checkbox, Loader, SimpleGrid, Stack, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { problemMessage } from '../api/problem';
import { groupState, toggleGroup, togglePermission } from './permissionSelection';
import type { PermissionGroup } from './roleApi';
import { usePermissionGroups } from './useRoles';

interface Props {
  /** Selected permission keys. */
  value: readonly string[];
  onChange: (next: string[]) => void;
  disabled?: boolean;
  /** Catalog override (tests); by default it is loaded from the API. */
  groups?: readonly PermissionGroup[];
}

const TOUCH = 44;

/** Grouped permission checkboxes (CONTEXT.md 5.2 groups); each group has a select-all checkbox. */
export function PermissionEditor({ value, onChange, disabled, groups }: Props) {
  const { t } = useTranslation();
  const query = usePermissionGroups();
  const catalog = groups ?? query.data ?? [];

  if (!groups && query.isLoading) return <Loader size="sm" />;
  if (!groups && query.isError) return <Alert color="red">{t(problemMessage(query.error).key)}</Alert>;

  return (
    <Stack gap="md" data-testid="permission-editor">
      {catalog.map((group) => {
        const state = groupState(value, group);
        return (
          <Stack key={group.code} gap="xs" role="group" aria-label={group.label}>
            <Checkbox
              label={<Text fw={600}>{group.label}</Text>}
              aria-label={t('roles.selectGroup', { group: group.label })}
              checked={state === 'all'}
              indeterminate={state === 'some'}
              disabled={disabled}
              onChange={(e) => onChange(toggleGroup(value, group, e.currentTarget.checked))}
              styles={{ root: { minHeight: TOUCH, display: 'flex', alignItems: 'center' } }}
            />
            <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="xs" pl="md">
              {group.permissions.map((p) => (
                <Checkbox
                  key={p.key}
                  label={p.label}
                  description={p.key}
                  checked={value.includes(p.key)}
                  disabled={disabled}
                  onChange={(e) => onChange(togglePermission(value, p.key, e.currentTarget.checked))}
                  styles={{ root: { minHeight: TOUCH, display: 'flex', alignItems: 'center' } }}
                />
              ))}
            </SimpleGrid>
          </Stack>
        );
      })}
    </Stack>
  );
}
