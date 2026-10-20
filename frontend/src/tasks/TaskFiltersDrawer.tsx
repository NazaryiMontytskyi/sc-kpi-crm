import { Button, Drawer, Group, Select, Stack, Switch, TagsInput, TextInput } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { fullName } from '../members/memberFormat';
import { useMembers } from '../members/useMembers';
import { DEFAULT_QUERY } from './taskLogic';
import { RESOLUTION_STATE_LIST, type SpaceSummary, type TaskQuery } from './tasksApi';
import { useSpace } from './useTasks';

interface Props {
  opened: boolean;
  onClose: () => void;
  query: TaskQuery;
  onChange: (next: TaskQuery) => void;
  spaces: SpaceSummary[];
  canSeeArchived: boolean;
}

/** All task-list filters live here (a drawer keeps the 375 px list uncluttered). Changes apply immediately. */
export function TaskFiltersDrawer({ opened, onClose, query, onChange, spaces, canSeeArchived }: Props) {
  const { t } = useTranslation();
  const space = useSpace(query.spaceId);
  const members = useMembers(false);
  const memberOptions = (members.data ?? []).filter((m) => !m.archived).map((m) => ({ value: m.id, label: fullName(m) }));
  const set = (patch: Partial<TaskQuery>) => onChange({ ...query, ...patch, page: 0 });
  const statuses = (space.data?.statuses ?? []).filter((s) => !s.archived);

  return (
    <Drawer opened={opened} onClose={onClose} position="right" title={t('tasks.filters.title')} closeButtonProps={{ 'aria-label': t('tasks.close'), size: 'lg' }}>
      <Stack gap="sm">
        <Select
          label={t('tasks.spaceLabel')}
          data={spaces.filter((s) => !s.archived || query.includeArchived).map((s) => ({ value: s.id, label: s.name }))}
          value={query.spaceId ?? null}
          onChange={(v) => set({ spaceId: v ?? undefined, statusId: undefined })}
          clearable
          searchable
          size="md"
        />
        <Select
          label={t('tasks.fields.status')}
          description={query.spaceId ? undefined : t('tasks.filters.statusNeedsSpace')}
          data={statuses.map((s) => ({ value: s.id, label: s.name }))}
          value={query.statusId ?? null}
          onChange={(v) => set({ statusId: v ?? undefined })}
          disabled={!query.spaceId}
          clearable
          size="md"
        />
        <Select
          label={t('tasks.filters.executor')}
          description={t('tasks.filters.executorHint')}
          data={memberOptions}
          value={query.executorId ?? null}
          onChange={(v) => set({ executorId: v ?? undefined })}
          clearable
          searchable
          size="md"
        />
        <Select
          label={t('tasks.filters.resolutionAuthor')}
          data={memberOptions}
          value={query.authorId ?? null}
          onChange={(v) => set({ authorId: v ?? undefined })}
          clearable
          searchable
          size="md"
        />
        <Select
          label={t('tasks.filters.resolutionState')}
          data={RESOLUTION_STATE_LIST.map((st) => ({ value: st, label: t(`resolutions.state.${st}`) }))}
          value={query.resolutionState ?? null}
          onChange={(v) => set({ resolutionState: (v as TaskQuery['resolutionState']) ?? undefined })}
          clearable
          size="md"
        />
        <TextInput label={t('tasks.filters.dueFrom')} type="date" value={query.dueFrom ?? ''} onChange={(e) => set({ dueFrom: e.currentTarget.value || undefined })} size="md" />
        <TextInput label={t('tasks.filters.dueTo')} type="date" value={query.dueTo ?? ''} onChange={(e) => set({ dueTo: e.currentTarget.value || undefined })} size="md" />
        <TagsInput label={t('tasks.fields.labels')} description={t('tasks.filters.labelsHint')} value={query.labels} onChange={(labels) => set({ labels })} splitChars={[',']} size="md" />
        {canSeeArchived && (
          <Switch
            label={t('tasks.filters.includeArchived')}
            checked={query.includeArchived}
            onChange={(e) => set({ includeArchived: e.currentTarget.checked })}
            styles={{ root: { minHeight: 44, display: 'flex', alignItems: 'center' } }}
          />
        )}
        <Group justify="space-between" mt="xs">
          <Button
            variant="default"
            mih={44}
            onClick={() => onChange({ ...DEFAULT_QUERY, sortBy: query.sortBy, direction: query.direction, size: query.size })}
          >
            {t('tasks.filters.reset')}
          </Button>
          <Button mih={44} onClick={onClose}>
            {t('tasks.filters.done')}
          </Button>
        </Group>
      </Stack>
    </Drawer>
  );
}
