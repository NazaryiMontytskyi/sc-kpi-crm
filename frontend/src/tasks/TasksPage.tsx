import { Alert, Anchor, Box, Button, Group, Loader, Pagination, Select, Stack, Tabs, Text, Title } from '@mantine/core';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import { useEffect, useRef, useState, type RefObject } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { TaskBoard } from './BoardPage';
import { MyTasksOverview } from './MyTasksOverview';
import { QuickViews } from './QuickViews';
import { TaskCard } from './TaskCard';
import { TaskFiltersDrawer } from './TaskFiltersDrawer';
import { TaskFormModal } from './TaskFormModal';
import {
  activeFilterCount,
  DEFAULT_QUERY,
  PANEL_DESKTOP_QUERY,
  TASKS_ARCHIVE,
  TASKS_CREATE,
  taskErrorKey,
  trackerSearch,
  trackerSection,
  TRACKER_SECTIONS,
  type TrackerSection,
} from './taskLogic';
import type { SortDirection, TaskQuery, TaskSortField } from './tasksApi';
import { useSelectedTaskId } from './trackerNav';
import { useSpaceDetails, useSpaces, useTaskList } from './useTasks';

const SORTS: { value: `${TaskSortField}:${SortDirection}`; key: string }[] = [
  { value: 'createdAt:desc', key: 'newest' },
  { value: 'createdAt:asc', key: 'oldest' },
  { value: 'deadline:asc', key: 'dueSoon' },
  { value: 'deadline:desc', key: 'dueLate' },
  { value: 'title:asc', key: 'titleAsc' },
  { value: 'title:desc', key: 'titleDesc' },
  { value: 'updatedAt:desc', key: 'updated' },
];

const TOUCH = 44;

interface ListProps {
  query: TaskQuery;
  onQueryChange: (q: TaskQuery) => void;
  selectedId: string | undefined;
}

/** Task list: cards (readable at 375 px), sorting in the toolbar, every filter inside a drawer. */
function TaskListView({ query, onQueryChange: setQuery, selectedId }: ListProps) {
  const { t } = useTranslation();
  const canSeeArchived = usePermission(TASKS_ARCHIVE);
  const [drawer, drawerHandlers] = useDisclosure(false);
  const spaces = useSpaces(true).data ?? [];
  const list = useTaskList(query);
  const items = list.data?.items ?? [];
  const details = useSpaceDetails([...new Set(items.map((i) => i.spaceId))]);
  const filterCount = activeFilterCount(query) + (query.view ? 1 : 0);

  return (
    <Stack gap="md">
      <QuickViews query={query} onChange={setQuery} />

      <Group gap="sm" align="flex-end">
        <Button variant="default" mih={TOUCH} onClick={drawerHandlers.open}>
          {t('tasks.filters.open')}
          {filterCount > 0 ? ` (${filterCount})` : ''}
        </Button>
        <Select
          aria-label={t('tasks.sort.label')}
          data={SORTS.map((s) => ({ value: s.value, label: t(`tasks.sort.${s.key}`) }))}
          value={`${query.sortBy}:${query.direction}`}
          onChange={(v) => {
            if (!v) return;
            const [sortBy, direction] = v.split(':') as [TaskSortField, SortDirection];
            setQuery({ ...query, sortBy, direction, page: 0 });
          }}
          allowDeselect={false}
          size="md"
          style={{ flex: 1, minWidth: 160 }}
        />
      </Group>

      {list.isLoading && <Loader size="sm" />}
      {list.isError && <Alert color="red">{t(taskErrorKey(list.error))}</Alert>}
      {list.data && items.length === 0 && <Text c="dimmed">{t(filterCount > 0 ? 'tasks.emptyFiltered' : 'tasks.empty')}</Text>}

      <Stack gap="xs" data-testid="task-list" role="list" aria-label={t('tasks.panel.listLabel')}>
        {items.map((task) => (
          <div role="listitem" key={task.id}>
            <TaskCard task={task} space={details.get(task.spaceId)} selected={task.id === selectedId} />
          </div>
        ))}
      </Stack>

      {list.data && list.data.totalPages > 1 && (
        <Pagination
          total={list.data.totalPages}
          value={query.page + 1}
          onChange={(p) => setQuery({ ...query, page: p - 1 })}
          size="md"
        />
      )}

      <TaskFiltersDrawer
        opened={drawer}
        onClose={drawerHandlers.close}
        query={query}
        onChange={setQuery}
        spaces={spaces}
        canSeeArchived={canSeeArchived}
      />
    </Stack>
  );
}

/**
 * When the panel closes, focus goes back to the card link of the task that was open (or to the page heading when
 * that task is not on screen, e.g. after a deep link), spec UI-8.
 */
function useFocusReturn(selectedId: string | undefined, fallback: RefObject<HTMLElement | null>) {
  const previous = useRef(selectedId);
  useEffect(() => {
    const last = previous.current;
    previous.current = selectedId;
    if (!last || selectedId) return;
    const link = Array.from(document.querySelectorAll<HTMLElement>('[data-task-link]')).find((el) => el.dataset.taskLink === last);
    (link ?? fallback.current)?.focus();
  }, [selectedId, fallback]);
}

/**
 * Master/detail tracker (spec UI-1): the sections "My tasks" (default, spec UI-4), "Issued by me" and "All tasks"
 * or the board on the left, and the task panel of the nested route `/tasks/:id` on the right (>= 1024 px) or over it
 * (tablet overlay, phone sheet). The section (`section`), the view (`layout=board`) and the board's space live in
 * the URL, so they survive opening and closing tasks.
 */
export function TasksPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const board = params.get('layout') === 'board';
  const boardSpace = params.get('space') ?? undefined;
  const section = trackerSection(params.get('section'));
  usePageTitle(board ? 'tasks.board.title' : 'nav.tasks');
  const canCreate = usePermission(TASKS_CREATE);
  const [query, setQuery] = useState<TaskQuery>({ ...DEFAULT_QUERY, spaceId: params.get('space') ?? undefined });
  const [creating, setCreating] = useState(false);
  const spaces = useSpaces(true).data ?? [];
  const selectedId = useSelectedTaskId();
  const desktop = useMediaQuery(PANEL_DESKTOP_QUERY, false, { getInitialValueInEffect: false });
  const sideBySide = desktop && !!selectedId;
  const headingRef = useRef<HTMLHeadingElement>(null);
  useFocusReturn(selectedId, headingRef);

  const boardSearch = new URLSearchParams({ layout: 'board' });
  const spaceForBoard = boardSpace ?? query.spaceId;
  if (spaceForBoard) boardSearch.set('space', spaceForBoard);
  // Leaving the board returns to the list of all tasks of the board's space.
  const listSearch = new URLSearchParams({ section: 'all' });
  if (boardSpace) listSearch.set('space', boardSpace);

  const openSection = (next: TrackerSection) => {
    const search = new URLSearchParams(trackerSearch(location.search));
    if (next === 'my') search.delete('section');
    else search.set('section', next);
    const out = search.toString();
    navigate({ pathname: location.pathname, search: out ? `?${out}` : '' });
  };

  return (
    <Stack gap="md">
      <Group justify="space-between" align="center" gap="sm">
        <Title order={1} size="h2" ref={headingRef} tabIndex={-1}>
          {t(board ? 'tasks.board.title' : 'tasks.title')}
        </Title>
        {board ? (
          <Anchor
            component={Link}
            to={{ pathname: location.pathname, search: `?${listSearch.toString()}` }}
            onClick={() => boardSpace && setQuery({ ...query, spaceId: boardSpace, page: 0 })}
            size="sm"
            style={{ display: 'inline-block', minHeight: TOUCH, paddingBlock: 12 }}
          >
            {t('tasks.board.list')}
          </Anchor>
        ) : (
          <Anchor
            component={Link}
            to={{ pathname: location.pathname, search: `?${boardSearch.toString()}` }}
            size="sm"
            style={{ display: 'inline-block', minHeight: TOUCH, paddingBlock: 12 }}
          >
            {t('tasks.board.open')}
          </Anchor>
        )}
        {canCreate && (
          <Button mih={TOUCH} onClick={() => setCreating(true)}>
            {t('tasks.create')}
          </Button>
        )}
      </Group>

      <Box
        data-testid="tracker"
        data-panel={sideBySide ? 'side' : undefined}
        style={
          sideBySide
            ? {
                display: 'grid',
                gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 3fr)',
                gap: 'var(--mantine-spacing-md)',
                alignItems: 'start',
              }
            : undefined
        }
      >
        <Box style={{ minWidth: 0 }}>
          {board ? (
            <TaskBoard spaceId={boardSpace} selectedId={selectedId} />
          ) : (
            <Tabs value={section} onChange={(v) => openSection(trackerSection(v))} keepMounted={false}>
              <Tabs.List aria-label={t('tasks.sections.label')}>
                {TRACKER_SECTIONS.map((s) => (
                  <Tabs.Tab key={s} value={s} mih={TOUCH}>
                    {t(`tasks.sections.${s}`)}
                  </Tabs.Tab>
                ))}
              </Tabs.List>
              <Tabs.Panel value="my" pt="md">
                <MyTasksOverview view="MY_TASKS" selectedId={selectedId} />
              </Tabs.Panel>
              <Tabs.Panel value="issued" pt="md">
                <MyTasksOverview view="ISSUED_BY_ME" selectedId={selectedId} />
              </Tabs.Panel>
              <Tabs.Panel value="all" pt="md">
                <TaskListView query={query} onQueryChange={setQuery} selectedId={selectedId} />
              </Tabs.Panel>
            </Tabs>
          )}
        </Box>
        <Outlet />
      </Box>

      {creating && (
        <TaskFormModal
          task={null}
          spaces={spaces}
          defaultSpaceId={board ? boardSpace : query.spaceId}
          onClose={() => setCreating(false)}
          onSaved={(created) => {
            setCreating(false);
            navigate(
              { pathname: `/tasks/${created.id}`, search: trackerSearch(location.search) },
              { state: { fromTracker: true }, replace: !!selectedId },
            );
          }}
        />
      )}
    </Stack>
  );
}
