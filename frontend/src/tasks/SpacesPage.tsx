import { Alert, Anchor, Badge, Button, Card, Group, Loader, Stack, Switch, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { SpaceFormModal } from './SpaceModals';
import { SPACES_MANAGE, taskErrorKey } from './taskLogic';
import { useSpaces } from './useTasks';

/** Spaces list (visible with `tasks.view`); creating and opening the settings need `spaces.manage`. */
export function SpacesPage() {
  const { t } = useTranslation();
  usePageTitle('nav.spaces');
  const navigate = useNavigate();
  const canManage = usePermission(SPACES_MANAGE);
  const [showArchived, setShowArchived] = useState(false);
  const [creating, setCreating] = useState(false);
  const query = useSpaces(canManage && showArchived);
  const spaces = query.data ?? [];

  return (
    <Stack gap="md">
      <Group justify="space-between" align="center" gap="sm">
        <Title order={1} size="h2">
          {t('tasks.spaces.title')}
        </Title>
        {canManage && (
          <Button mih={44} onClick={() => setCreating(true)}>
            {t('tasks.spaces.create')}
          </Button>
        )}
      </Group>
      {canManage && (
        <Switch
          label={t('tasks.spaces.showArchived')}
          checked={showArchived}
          onChange={(e) => setShowArchived(e.currentTarget.checked)}
          styles={{ root: { minHeight: 44, display: 'flex', alignItems: 'center' } }}
        />
      )}
      {query.isLoading && <Loader size="sm" />}
      {query.isError && <Alert color="red">{t(taskErrorKey(query.error))}</Alert>}
      {query.data && spaces.length === 0 && <Text c="dimmed">{t('tasks.spaces.empty')}</Text>}
      <Stack gap="xs" data-testid="space-list">
        {spaces.map((s) => (
          <Card key={s.id} withBorder padding="sm" component="article">
            <Group justify="space-between" wrap="nowrap" gap="sm">
              <Group gap="xs" style={{ minWidth: 0 }}>
                {canManage ? (
                  <Anchor component={Link} to={`/spaces/${s.id}`} fw={600} style={{ overflowWrap: 'anywhere' }}>
                    {s.name}
                  </Anchor>
                ) : (
                  <Text fw={600} style={{ overflowWrap: 'anywhere' }}>
                    {s.name}
                  </Text>
                )}
                {s.general && (
                  <Badge variant="light" size="sm">
                    {t('tasks.spaces.generalBadge')}
                  </Badge>
                )}
                {s.archived && (
                  <Badge variant="outline" color="gray" size="sm">
                    {t('tasks.archivedBadge')}
                  </Badge>
                )}
              </Group>
              <Group gap="md" wrap="nowrap">
              <Anchor component={Link} to={`/tasks?space=${s.id}`} size="sm" style={{ whiteSpace: 'nowrap' }}>
                {t('tasks.spaces.openTasks')}
              </Anchor>
                <Anchor component={Link} to={`/tasks/board?space=${s.id}`} size="sm" style={{ whiteSpace: 'nowrap' }}>
                  {t('tasks.board.open')}
                </Anchor>
              </Group>
            </Group>
          </Card>
        ))}
      </Stack>
      {creating && <SpaceFormModal space={null} onClose={() => setCreating(false)} onSaved={(s) => navigate(`/spaces/${s.id}`)} />}
    </Stack>
  );
}
