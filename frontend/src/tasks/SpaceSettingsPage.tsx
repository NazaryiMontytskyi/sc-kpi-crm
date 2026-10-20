import { ActionIcon, Alert, Anchor, Badge, Button, Card, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { ForbiddenPage } from '../pages/ErrorPages';
import { ArchiveStatusModal, CustomFieldFormModal, SpaceFormModal, StatusFormModal } from './SpaceModals';
import { moveItem, SPACES_MANAGE, taskErrorKey } from './taskLogic';
import type { CustomField, Space, SpaceStatus } from './tasksApi';
import { useArchiveCustomField, useArchiveSpace, useReorderStatuses, useRestoreSpace, useSpace } from './useTasks';

function StatusesEditor({ space }: { space: Space }) {
  const { t } = useTranslation();
  const reorder = useReorderStatuses();
  const active = space.statuses.filter((s) => !s.archived);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<SpaceStatus | null>(null);
  const [archiving, setArchiving] = useState<SpaceStatus | null>(null);
  const [failure, setFailure] = useState<unknown>(null);

  const move = (from: number, to: number) => {
    setFailure(null);
    reorder.mutate({ spaceId: space.id, ids: moveItem(active, from, to).map((s) => s.id) }, { onError: setFailure });
  };

  return (
    <Stack gap="xs" component="section" aria-labelledby="statuses-title">
      <Group justify="space-between">
        <Title order={2} size="h4" id="statuses-title">
          {t('tasks.status.title')}
        </Title>
        <Button mih={44} onClick={() => setAdding(true)} disabled={space.archived}>
          {t('tasks.status.add')}
        </Button>
      </Group>
      {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
      <Stack gap="xs" data-testid="status-list">
        {active.map((s, i) => (
          <Card key={s.id} withBorder padding="xs">
            <Group justify="space-between" wrap="wrap" gap="xs">
              <Group gap="xs" style={{ minWidth: 0 }}>
                <Text fw={500} style={{ overflowWrap: 'anywhere' }}>
                  {s.name}
                </Text>
                <Badge variant="light" color="gray" size="sm">
                  {t(`tasks.status.categories.${s.category}`)}
                </Badge>
              </Group>
              <Group gap={4}>
                <ActionIcon size={44} variant="subtle" color="gray" aria-label={t('tasks.status.moveUp', { name: s.name })} disabled={i === 0 || reorder.isPending || space.archived} onClick={() => move(i, i - 1)}>
                  <span aria-hidden>↑</span>
                </ActionIcon>
                <ActionIcon size={44} variant="subtle" color="gray" aria-label={t('tasks.status.moveDown', { name: s.name })} disabled={i === active.length - 1 || reorder.isPending || space.archived} onClick={() => move(i, i + 1)}>
                  <span aria-hidden>↓</span>
                </ActionIcon>
                <Button variant="default" mih={44} disabled={space.archived} onClick={() => setEditing(s)} aria-label={t('tasks.status.editAria', { name: s.name })}>
                  {t('tasks.edit')}
                </Button>
                <Button variant="default" mih={44} disabled={space.archived || active.length <= 1} onClick={() => setArchiving(s)} aria-label={t('tasks.status.archiveAria', { name: s.name })}>
                  {t('tasks.archive')}
                </Button>
              </Group>
            </Group>
          </Card>
        ))}
      </Stack>
      {adding && <StatusFormModal spaceId={space.id} status={null} onClose={() => setAdding(false)} />}
      {editing && <StatusFormModal spaceId={space.id} status={editing} onClose={() => setEditing(null)} />}
      {archiving && (
        <ArchiveStatusModal spaceId={space.id} status={archiving} others={active.filter((s) => s.id !== archiving.id)} onClose={() => setArchiving(null)} />
      )}
    </Stack>
  );
}

function CustomFieldsEditor({ space }: { space: Space }) {
  const { t } = useTranslation();
  const archive = useArchiveCustomField();
  const fields = space.customFields.filter((f) => !f.archived);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<CustomField | null>(null);
  const [failure, setFailure] = useState<unknown>(null);

  return (
    <Stack gap="xs" component="section" aria-labelledby="fields-title">
      <Group justify="space-between">
        <Title order={2} size="h4" id="fields-title">
          {t('tasks.customField.title')}
        </Title>
        <Button mih={44} onClick={() => setAdding(true)} disabled={space.archived}>
          {t('tasks.customField.add')}
        </Button>
      </Group>
      {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
      {fields.length === 0 && <Text c="dimmed">{t('tasks.customField.empty')}</Text>}
      <Stack gap="xs" data-testid="field-list">
        {fields.map((f) => (
          <Card key={f.id} withBorder padding="xs">
            <Group justify="space-between" wrap="wrap" gap="xs">
              <Group gap="xs" style={{ minWidth: 0 }}>
                <Text fw={500} style={{ overflowWrap: 'anywhere' }}>
                  {f.name}
                </Text>
                <Badge variant="light" color="gray" size="sm">
                  {t(`tasks.customField.types.${f.type}`)}
                </Badge>
                {f.required && (
                  <Badge variant="light" color="orange" size="sm">
                    {t('tasks.customField.requiredBadge')}
                  </Badge>
                )}
              </Group>
              <Group gap={4}>
                <Button variant="default" mih={44} disabled={space.archived} onClick={() => setEditing(f)} aria-label={t('tasks.customField.editAria', { name: f.name })}>
                  {t('tasks.edit')}
                </Button>
                <Button
                  variant="default"
                  mih={44}
                  disabled={space.archived || archive.isPending}
                  aria-label={t('tasks.customField.archiveAria', { name: f.name })}
                  onClick={() => {
                    setFailure(null);
                    archive.mutate({ spaceId: space.id, fieldId: f.id }, { onError: setFailure });
                  }}
                >
                  {t('tasks.archive')}
                </Button>
              </Group>
            </Group>
          </Card>
        ))}
      </Stack>
      {adding && <CustomFieldFormModal spaceId={space.id} field={null} onClose={() => setAdding(false)} />}
      {editing && <CustomFieldFormModal spaceId={space.id} field={editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

/** Space settings: name / unit binding, statuses editor and custom fields editor. Needs `spaces.manage`. */
export function SpaceSettingsPage() {
  const { t } = useTranslation();
  usePageTitle('nav.spaces');
  const { id = '' } = useParams();
  const canManage = usePermission(SPACES_MANAGE);
  const query = useSpace(canManage ? id : undefined);
  const archive = useArchiveSpace();
  const restore = useRestoreSpace();
  const [editing, setEditing] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);

  if (!canManage) return <ForbiddenPage />;
  if (query.isLoading) return <Loader size="sm" />;
  const space = query.data;
  if (!space) return <Alert color="red">{t(query.error ? taskErrorKey(query.error) : 'tasks.loadFailed')}</Alert>;

  return (
    <Stack gap="md">
      <Anchor component={Link} to="/spaces" size="sm">
        {t('tasks.spaces.back')}
      </Anchor>
      <Group justify="space-between" align="flex-start" gap="sm">
        <Group gap="xs" style={{ minWidth: 0, flex: 1 }}>
          <Title order={1} size="h2" style={{ overflowWrap: 'anywhere' }}>
            {space.name}
          </Title>
          {space.general && (
            <Badge variant="light" size="sm">
              {t('tasks.spaces.generalBadge')}
            </Badge>
          )}
          {space.archived && (
            <Badge variant="outline" color="gray" size="sm">
              {t('tasks.archivedBadge')}
            </Badge>
          )}
        </Group>
        <Group gap="xs">
          {!space.archived && (
            <Button mih={44} onClick={() => setEditing(true)}>
              {t('tasks.edit')}
            </Button>
          )}
          <Button
            mih={44}
            variant="default"
            loading={archive.isPending || restore.isPending}
            onClick={() => {
              setFailure(null);
              (space.archived ? restore : archive).mutate(space.id, { onError: setFailure });
            }}
          >
            {t(space.archived ? 'tasks.restore' : 'tasks.archive')}
          </Button>
        </Group>
      </Group>
      {failure !== null && <Alert color="red">{t(taskErrorKey(failure))}</Alert>}
      <StatusesEditor space={space} />
      <CustomFieldsEditor space={space} />
      {editing && <SpaceFormModal space={space} onClose={() => setEditing(false)} />}
    </Stack>
  );
}
