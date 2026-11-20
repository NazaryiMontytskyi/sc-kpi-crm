import { Alert, Anchor, Badge, Box, Button, Card, CloseButton, Drawer, Group, Loader, Paper, Stack, Tabs, Text, Title } from '@mantine/core';
import { useMediaQuery, useReducedMotion } from '@mantine/hooks';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { formatKyivDateTime } from '../audit/kyivTime';
import { useAuth, usePermission } from '../auth/CurrentUser';
import type { MemberSummary } from '../members/memberApi';
import { formatDate, fullName } from '../members/memberFormat';
import { useMembers } from '../members/useMembers';
import { RichTextView } from '../shared/RichTextField';
import { CommentsPanel } from './CommentsPanel';
import { PanelArtifacts } from './PanelArtifacts';
import { PanelAssignees } from './PanelAssignees';
import { PanelDeadline } from './PanelDeadline';
import { PanelStatus } from './PanelStatus';
import { PanelSubtasks } from './PanelSubtasks';
import { ResolutionsPanel } from './ResolutionsPanel';
import { SuggestCloseBanner } from './SuggestCloseBanner';
import { SuggestDoneBanner } from './SuggestDoneBanner';
import { TaskAttachments } from './TaskAttachments';
import { TaskFormModal } from './TaskFormModal';
import {
  canChangeStatus,
  canEditTask,
  isOpenAndOverdue,
  isVersionConflict,
  PANEL_DESKTOP_QUERY,
  PANEL_TABLET_QUERY,
  panelVariant,
  priorityColor,
  TASKS_ARCHIVE,
  taskErrorKey,
  todayKyiv,
  type PanelVariant,
} from './taskLogic';
import type { CustomField, Task } from './tasksApi';
import { useClosePanel, useTaskLink } from './trackerNav';
import { useArchiveTask, useRestoreTask, useSpace, useSpaces, useTask } from './useTasks';

/** Minimum touch target in px. */
const TOUCH = 44;
/** Width of the tablet overlay. */
const OVERLAY_WIDTH = 560;
const TRANSITION_MS = 200;

// ---- small read-only blocks ------------------------------------------------------------------

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box>
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      {children}
    </Box>
  );
}

function FieldValue({ field, value, members }: { field: CustomField; value: unknown; members: readonly MemberSummary[] }) {
  const { t } = useTranslation();
  if (value === undefined || value === null || value === '') return <Text c="dimmed">{t('tasks.notSet')}</Text>;
  if (field.type === 'DATE') return <Text>{formatDate(String(value))}</Text>;
  if (field.type === 'PERSON') {
    const m = members.find((x) => x.id === value);
    return <Text>{m ? fullName(m) : '…'}</Text>;
  }
  return <Text style={{ overflowWrap: 'anywhere' }}>{String(value)}</Text>;
}

/** Edit failure: a 409 shows a reload hint with a reload button (spec §6.5); anything else its i18n message. */
function FailureAlert({ failure, onReload, onDismiss }: { failure: unknown; onReload: () => void; onDismiss: () => void }) {
  const { t } = useTranslation();
  if (isVersionConflict(failure)) {
    return (
      <Alert color="yellow" role="alert" data-testid="version-conflict" title={t('tasks.panel.conflictTitle')}>
        <Stack gap="xs" align="flex-start">
          <Text size="sm">{t('tasks.errors.TASK_VERSION_CONFLICT')}</Text>
          <Button mih={TOUCH} variant="default" onClick={onReload}>
            {t('tasks.panel.reload')}
          </Button>
        </Stack>
      </Alert>
    );
  }
  return (
    <Alert color="red" role="alert" withCloseButton closeButtonLabel={t('tasks.close')} onClose={onDismiss}>
      {t(taskErrorKey(failure))}
    </Alert>
  );
}

// ---- panel body ------------------------------------------------------------------------------

interface BodyProps {
  task: Task;
  onReload: () => void;
}

/**
 * Everything about one task without navigating away (spec UI-2) with quick actions in place (UI-5): status,
 * deadline, assignees, subtasks and artifacts. Every control follows the user's permissions; the backend decides.
 */
function TaskPanelBody({ task, onReload }: BodyProps) {
  const { t, i18n } = useTranslation();
  const [searchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  // Deep links from notifications open the matching tab.
  const initialTab = tabParam === 'resolutions' || tabParam === 'history' ? tabParam : 'comments';
  const { user } = useAuth();
  const canArchive = usePermission(TASKS_ARCHIVE);
  const space = useSpace(task.spaceId);
  const spaces = useSpaces(true);
  const members = useMembers(false);
  const memberList = members.data ?? [];
  const archive = useArchiveTask();
  const restore = useRestoreTask();
  const linkTo = useTaskLink();
  const [editing, setEditing] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);

  const editable = canEditTask(user, task) && !task.archived;
  const statusEditable = canChangeStatus(user, task) && !task.archived;
  const allStatuses = space.data?.statuses ?? [];
  const current = allStatuses.find((s) => s.id === task.statusId);
  const overdue = isOpenAndOverdue(task, todayKyiv(), current?.category);
  const fields = (space.data?.customFields ?? []).filter((f) => !f.archived || task.customFieldValues[f.id] !== undefined);
  const spaceName = spaces.data?.find((s) => s.id === task.spaceId)?.name ?? space.data?.name;
  const clearFailure = () => setFailure(null);
  const run = (fn: () => void) => {
    setFailure(null);
    fn();
  };
  const reload = () => {
    setFailure(null);
    onReload();
  };

  return (
    <Stack gap="md" data-testid="task-panel-body">
      {task.parentId && (
        <Anchor component={Link} {...linkTo(task.parentId)} size="sm" style={{ display: 'inline-block', minHeight: TOUCH, paddingBlock: 10 }}>
          {t('tasks.panel.openParent')}
        </Anchor>
      )}
      {(editable || canArchive) && (
        <Group gap="xs">
          {editable && (
            <Button mih={TOUCH} onClick={() => setEditing(true)}>
              {t('tasks.edit')}
            </Button>
          )}
          {canArchive && !task.archived && (
            <Button
              mih={TOUCH}
              variant="default"
              loading={archive.isPending}
              onClick={() => run(() => archive.mutate(task.id, { onError: setFailure }))}
            >
              {t('tasks.archive')}
            </Button>
          )}
          {canArchive && task.archived && (
            <Button
              mih={TOUCH}
              variant="default"
              loading={restore.isPending}
              onClick={() => run(() => restore.mutate(task.id, { onError: setFailure }))}
            >
              {t('tasks.restore')}
            </Button>
          )}
        </Group>
      )}
      {failure !== null && <FailureAlert failure={failure} onReload={reload} onDismiss={() => setFailure(null)} />}
      {task.archived && <Alert color="gray">{t('tasks.archivedNotice')}</Alert>}
      <SuggestCloseBanner task={task} statuses={allStatuses} editable={statusEditable} onFailure={setFailure} onStart={clearFailure} />
      <SuggestDoneBanner task={task} statuses={allStatuses} />

      <Card withBorder padding="sm">
        <Stack gap="sm">
          <PanelStatus task={task} statuses={allStatuses} editable={statusEditable} onFailure={setFailure} onStart={clearFailure} />
          <PanelAssignees task={task} members={memberList} />
          <Group gap="lg" align="flex-start" wrap="wrap">
            <DetailRow label={t('tasks.panel.issuedAt')}>
              <Text size="sm" data-testid="panel-issued">
                {task.issuedAt ? formatKyivDateTime(task.issuedAt, i18n.language) : t('tasks.notSet')}
              </Text>
            </DetailRow>
            <PanelDeadline key={`${task.id}:${task.deadline ?? ''}`} task={task} overdue={overdue} editable={editable} onConflict={setFailure} />
          </Group>
          <Group gap="xs" wrap="wrap">
            <Badge variant="light" color={priorityColor(task.priority)}>
              {t(`tasks.priority.${task.priority}`)}
            </Badge>
            {task.parentId && (
              <Badge variant="outline" color="gray">
                {t('tasks.panel.subtaskBadge')}
              </Badge>
            )}
            {spaceName && (
              <Text size="sm" c="dimmed">
                {spaceName}
              </Text>
            )}
          </Group>
          {task.labels.length > 0 && (
            <Group gap={4}>
              {task.labels.map((l) => (
                <Badge key={l} variant="outline" color="gray" size="sm" tt="none">
                  {l}
                </Badge>
              ))}
            </Group>
          )}
        </Stack>
      </Card>

      <Card withBorder padding="sm" component="section" aria-labelledby="task-panel-description">
        <Title order={3} size="h5" mb="xs" id="task-panel-description">
          {t('tasks.fields.description')}
        </Title>
        {task.description ? <RichTextView value={task.description} /> : <Text c="dimmed">{t('tasks.notSet')}</Text>}
      </Card>

      {fields.length > 0 && (
        <Card withBorder padding="sm">
          <Stack gap="xs">
            {fields.map((f) => (
              <DetailRow key={f.id} label={f.name}>
                <FieldValue field={f} value={task.customFieldValues[f.id]} members={memberList} />
              </DetailRow>
            ))}
          </Stack>
        </Card>
      )}

      <PanelArtifacts task={task} members={memberList} />
      {!task.parentId && <PanelSubtasks task={task} statuses={allStatuses} members={memberList} onConflict={setFailure} />}
      <TaskAttachments task={task} editable={editable} />

      {/* Comments and resolutions are separate requests made only when their tab is shown (spec §7). */}
      <Tabs defaultValue={initialTab} keepMounted={false}>
        <Tabs.List>
          <Tabs.Tab value="comments" mih={TOUCH}>
            {t('tasks.tabs.comments')}
          </Tabs.Tab>
          <Tabs.Tab value="resolutions" mih={TOUCH}>
            {t('tasks.tabs.resolutions')}
          </Tabs.Tab>
          <Tabs.Tab value="history" mih={TOUCH}>
            {t('tasks.tabs.history')}
          </Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="comments" pt="sm">
          <CommentsPanel targetType="TASK" targetId={task.id} readOnly={task.archived} />
        </Tabs.Panel>
        <Tabs.Panel value="resolutions" pt="sm">
          <ResolutionsPanel taskId={task.id} taskArchived={task.archived} />
        </Tabs.Panel>
        <Tabs.Panel value="history" pt="sm">
          <Text c="dimmed">{t('tasks.tabs.soon')}</Text>
        </Tabs.Panel>
      </Tabs>

      {editing && (
        <TaskFormModal task={task} spaces={spaces.data ?? []} onClose={() => setEditing(false)} onSaved={() => setEditing(false)} />
      )}
    </Stack>
  );
}

// ---- frames ----------------------------------------------------------------------------------

interface FrameProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

/** Desktop (>= 1024 px): a region next to the list; focus moves to its heading when another task opens. */
function SidePanel({ taskId, title, onClose, children }: FrameProps & { taskId: string }) {
  const { t } = useTranslation();
  const headingId = useId();
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    headingRef.current?.focus({ preventScroll: true });
  }, [taskId]);
  return (
    <Paper
      component="section"
      role="region"
      aria-labelledby={headingId}
      withBorder
      radius="md"
      p="md"
      data-testid="task-panel"
      data-variant="side"
      style={{
        position: 'sticky',
        top: 'calc(var(--app-shell-header-height, 60px) + var(--mantine-spacing-md))',
        maxHeight: 'calc(100dvh - var(--app-shell-header-height, 60px) - 2 * var(--mantine-spacing-md))',
        overflowY: 'auto',
        minWidth: 0,
      }}
    >
      <Group justify="space-between" align="flex-start" wrap="nowrap" mb="sm">
        <Title order={2} size="h3" id={headingId} ref={headingRef} tabIndex={-1} style={{ overflowWrap: 'anywhere', minWidth: 0 }}>
          {title}
        </Title>
        <CloseButton size={TOUCH} aria-label={t('tasks.panel.close')} onClick={onClose} />
      </Group>
      {children}
    </Paper>
  );
}

/**
 * Tablet overlay and phone full-screen sheet: a modal drawer with a focus trap (Mantine). It opens one frame
 * after mounting so the slide-in plays; under `prefers-reduced-motion` the transition has no duration.
 */
function DrawerPanel({ variant, title, onClose, children }: FrameProps & { variant: Exclude<PanelVariant, 'side'> }) {
  const { t } = useTranslation();
  const reduceMotion = useReducedMotion();
  const [opened, setOpened] = useState(false);
  useEffect(() => {
    const frame = requestAnimationFrame(() => setOpened(true));
    return () => cancelAnimationFrame(frame);
  }, []);
  const sheet = variant === 'sheet';
  return (
    <Drawer.Root
      opened={opened}
      onClose={onClose}
      position="right"
      size={sheet ? '100%' : OVERLAY_WIDTH}
      returnFocus={false}
      transitionProps={{ transition: 'slide-left', duration: reduceMotion ? 0 : TRANSITION_MS }}
    >
      <Drawer.Overlay />
      <Drawer.Content radius={0} data-testid="task-panel" data-variant={variant}>
        <Drawer.Header style={{ gap: 'var(--mantine-spacing-xs)', alignItems: 'flex-start' }}>
          {sheet && (
            <Button variant="default" mih={TOUCH} px="sm" onClick={onClose} style={{ flexShrink: 0 }} data-autofocus>
              {t('tasks.panel.back')}
            </Button>
          )}
          <Drawer.Title style={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere', paddingTop: sheet ? 10 : 0 }} fw={700} fz="h4">
            {title}
          </Drawer.Title>
          {!sheet && <Drawer.CloseButton size={TOUCH} aria-label={t('tasks.panel.close')} data-autofocus />}
        </Drawer.Header>
        <Drawer.Body style={{ overflowX: 'hidden' }}>{children}</Drawer.Body>
      </Drawer.Content>
    </Drawer.Root>
  );
}

/** One panel component in three presentations: side panel, tablet overlay, phone sheet (spec UI-1..UI-3, §6.7). */
export function TaskPanel({ taskId, variant, onClose }: { taskId: string; variant: PanelVariant; onClose: () => void }) {
  const { t } = useTranslation();
  // One request for task + assignees + subtasks + artifacts (spec §7).
  const query = useTask(taskId);
  const title = query.data?.title ?? t('tasks.panel.title');

  let content: ReactNode;
  if (query.isLoading) content = <Loader size="sm" aria-label={t('tasks.panel.loading')} />;
  else if (query.isError || !query.data) {
    const notFound = query.error instanceof ApiProblemError && query.error.status === 404;
    content = <Alert color="red">{t(notFound ? 'tasks.panel.notFound' : query.error ? taskErrorKey(query.error) : 'tasks.loadFailed')}</Alert>;
  } else content = <TaskPanelBody key={taskId} task={query.data} onReload={() => void query.refetch()} />;

  if (variant === 'side') {
    return (
      <SidePanel taskId={taskId} title={title} onClose={onClose}>
        {content}
      </SidePanel>
    );
  }
  return (
    <DrawerPanel variant={variant} title={title} onClose={onClose}>
      {content}
    </DrawerPanel>
  );
}

/** The current presentation of the panel for this viewport. */
export function usePanelVariant(): PanelVariant {
  const desktop = useMediaQuery(PANEL_DESKTOP_QUERY, false, { getInitialValueInEffect: false });
  const tablet = useMediaQuery(PANEL_TABLET_QUERY, false, { getInitialValueInEffect: false });
  return panelVariant(desktop, tablet);
}

/** Element of the nested route `/tasks/:id`: the panel next to (or over) the tracker. */
export function TaskPanelRoute() {
  const { id = '' } = useParams();
  const variant = usePanelVariant();
  const close = useClosePanel();
  return <TaskPanel taskId={id} variant={variant} onClose={close} />;
}
