import { ActionIcon, Alert, Button, Chip, Group, Menu, Modal, ScrollArea, Stack, Text, TextInput } from '@mantine/core';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { criteriaFromQuery, hasCriteria, queryFromCriteria, sameCriteria, taskErrorKey } from './taskLogic';
import { QUICK_VIEWS, type SavedFilter, type TaskQuery } from './tasksApi';
import { useCreateSavedFilter, useDeleteSavedFilter, useSavedFilters } from './useTasks';

const TOUCH = 44;
const MAX_NAME = 100;

interface Props {
  query: TaskQuery;
  onChange: (next: TaskQuery) => void;
}

/**
 * Quick views (built-in, evaluated by the backend for the current user) as a horizontally scrollable chip row, and the
 * caller's own saved filters in a menu. Applying a saved filter replaces all filters of the list.
 */
export function QuickViews({ query, onChange }: Props) {
  const { t } = useTranslation();
  const saved = useSavedFilters();
  const [saving, setSaving] = useState(false);
  const [managing, setManaging] = useState(false);
  const filters = saved.data ?? [];
  const current = criteriaFromQuery(query);

  return (
    <Stack gap="xs">
      <ScrollArea type="auto" scrollbars="x" offsetScrollbars>
        <Group gap="xs" wrap="nowrap" role="group" aria-label={t('tasks.quickViews.label')}>
          {QUICK_VIEWS.map((view) => (
            <Chip
              key={view}
              size="md"
              variant="light"
              checked={query.view === view}
              // clicking the active chip clears the view
              onChange={(on) => onChange({ ...query, view: on ? view : undefined, page: 0 })}
              styles={{ label: { minHeight: TOUCH, display: 'inline-flex', alignItems: 'center', whiteSpace: 'nowrap' } }}
            >
              {t(`tasks.quickViews.${view}`)}
            </Chip>
          ))}
        </Group>
      </ScrollArea>

      <Group gap="xs">
        <Menu position="bottom-start" withinPortal>
          <Menu.Target>
            <Button variant="default" mih={TOUCH}>
              {t('tasks.savedFilters.menu')}
              {filters.length > 0 ? ` (${filters.length})` : ''}
            </Button>
          </Menu.Target>
          <Menu.Dropdown>
            {filters.length === 0 && <Menu.Label>{t('tasks.savedFilters.none')}</Menu.Label>}
            {filters.map((f) => (
              <Menu.Item
                key={f.id}
                mih={TOUCH}
                onClick={() => onChange(queryFromCriteria(f.criteria, query))}
                fw={sameCriteria(f.criteria, current) ? 700 : undefined}
              >
                {f.name}
              </Menu.Item>
            ))}
            {filters.length > 0 && <Menu.Divider />}
            <Menu.Item mih={TOUCH} disabled={!hasCriteria(current)} onClick={() => setSaving(true)}>
              {t('tasks.savedFilters.saveCurrent')}
            </Menu.Item>
            <Menu.Item mih={TOUCH} disabled={filters.length === 0} onClick={() => setManaging(true)}>
              {t('tasks.savedFilters.manage')}
            </Menu.Item>
          </Menu.Dropdown>
        </Menu>
      </Group>

      {saving && <SaveFilterModal query={query} onClose={() => setSaving(false)} />}
      {managing && <ManageFiltersModal filters={filters} onClose={() => setManaging(false)} />}
    </Stack>
  );
}

function SaveFilterModal({ query, onClose }: { query: TaskQuery; onClose: () => void }) {
  const { t } = useTranslation();
  const create = useCreateSavedFilter();
  const [name, setName] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const nameError = name.trim().length === 0 ? 'tasks.savedFilters.nameRequired' : name.trim().length > MAX_NAME ? 'tasks.savedFilters.nameTooLong' : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (nameError) return;
    create.mutate({ name: name.trim(), criteria: criteriaFromQuery(query) }, { onSuccess: onClose });
  };

  return (
    <Modal opened onClose={onClose} title={t('tasks.savedFilters.saveTitle')} closeButtonProps={{ 'aria-label': t('tasks.close'), size: 'lg' }}>
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          {create.isError && <Alert color="red">{t(taskErrorKey(create.error))}</Alert>}
          <TextInput
            label={t('tasks.savedFilters.name')}
            value={name}
            onChange={(e) => setName(e.currentTarget.value)}
            error={submitted && nameError ? t(nameError) : undefined}
            data-autofocus
            size="md"
          />
          <Group justify="flex-end">
            <Button variant="default" mih={TOUCH} onClick={onClose} disabled={create.isPending}>
              {t('tasks.cancel')}
            </Button>
            <Button type="submit" mih={TOUCH} loading={create.isPending}>
              {t('tasks.save')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

function ManageFiltersModal({ filters, onClose }: { filters: SavedFilter[]; onClose: () => void }) {
  const { t } = useTranslation();
  const remove = useDeleteSavedFilter();
  return (
    <Modal opened onClose={onClose} title={t('tasks.savedFilters.manageTitle')} closeButtonProps={{ 'aria-label': t('tasks.close'), size: 'lg' }}>
      <Stack gap="xs">
        {remove.isError && <Alert color="red">{t(taskErrorKey(remove.error))}</Alert>}
        {filters.length === 0 && <Text c="dimmed">{t('tasks.savedFilters.none')}</Text>}
        {filters.map((f) => (
          <Group key={f.id} justify="space-between" wrap="nowrap" data-testid="saved-filter-row">
            <Text style={{ overflowWrap: 'anywhere' }}>{f.name}</Text>
            <ActionIcon
              variant="subtle"
              color="red"
              size={TOUCH}
              aria-label={t('tasks.savedFilters.remove', { name: f.name })}
              loading={remove.isPending && remove.variables === f.id}
              onClick={() => remove.mutate(f.id)}
            >
              ✕
            </ActionIcon>
          </Group>
        ))}
      </Stack>
    </Modal>
  );
}
