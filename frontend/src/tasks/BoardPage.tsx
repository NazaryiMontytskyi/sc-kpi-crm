import { Alert, Anchor, Badge, Box, Button, Card, Group, Loader, Menu, Paper, Select, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { usePageTitle } from '../layout/pageTitle';
import { formatDate } from '../members/memberFormat';
import { boardColumns, DRAG_MIME, groupByStatus } from './boardLogic';
import { isOverdue, priorityColor, taskErrorKey, todayKyiv } from './taskLogic';
import type { SpaceStatus, TaskSummary } from './tasksApi';
import { useBoardTasks, useMoveTask, useSpace, useSpaces } from './useTasks';

interface CardProps {
  task: TaskSummary;
  columns: SpaceStatus[];
  today: string;
  onMove: (taskId: string, statusId: string) => void;
}

/** A board card. The "Move to" menu works everywhere (touch, keyboard); dragging is a desktop shortcut. */
function BoardCard({ task, columns, today, onMove }: CardProps) {
  const { t } = useTranslation();
  const overdue = isOverdue(task, today);
  return (
    <Card
      withBorder
      padding="xs"
      component="article"
      data-testid="board-card"
      draggable
      onDragStart={(e) => {
        e.dataTransfer.setData(DRAG_MIME, task.id);
        e.dataTransfer.effectAllowed = 'move';
      }}
      style={overdue ? { borderColor: 'var(--mantine-color-red-6)' } : undefined}
    >
      <Stack gap={6}>
        <Anchor component={Link} to={`/tasks/${task.id}`} fw={600} size="sm" style={{ overflowWrap: 'anywhere' }}>
          {task.title}
        </Anchor>
        <Group gap={6} wrap="wrap">
          <Badge variant="light" color={priorityColor(task.priority)} size="xs">
            {t(`tasks.priority.${task.priority}`)}
          </Badge>
          {task.dueDate && (
            <Text size="xs" c={overdue ? 'red' : 'dimmed'} data-testid={overdue ? 'board-overdue' : undefined}>
              {t('tasks.due', { date: formatDate(task.dueDate) })}
              {overdue ? ` · ${t('tasks.overdue')}` : ''}
            </Text>
          )}
        </Group>
        {task.labels.length > 0 && (
          <Group gap={4}>
            {task.labels.map((l) => (
              <Badge key={l} variant="outline" color="gray" size="xs" tt="none">
                {l}
              </Badge>
            ))}
          </Group>
        )}
        <Menu position="bottom-start" withinPortal>
          <Menu.Target>
            <Button variant="default" size="xs" mih={44} aria-label={t('tasks.board.moveTitle', { title: task.title })}>
              {t('tasks.board.moveTo')}
            </Button>
          </Menu.Target>
          <Menu.Dropdown>
            {columns.map((c) => (
              <Menu.Item key={c.id} disabled={c.id === task.statusId} onClick={() => onMove(task.id, c.id)} mih={44}>
                {c.name}
              </Menu.Item>
            ))}
          </Menu.Dropdown>
        </Menu>
      </Stack>
    </Card>
  );
}

/** Kanban board of one space: columns are the space's statuses, cards move by menu or drag-and-drop. */
export function BoardPage() {
  const { t } = useTranslation();
  usePageTitle('tasks.board.title');
  const [params, setParams] = useSearchParams();
  const spaceId = params.get('space') ?? undefined;
  const [moveError, setMoveError] = useState<string | null>(null);
  const [dragOver, setDragOver] = useState<string | null>(null);

  const spaces = useSpaces(false);
  const space = useSpace(spaceId);
  const tasks = useBoardTasks(spaceId);
  const move = useMoveTask(spaceId);

  const columns = boardColumns(space.data?.statuses ?? []);
  const grouped = groupByStatus(tasks.data ?? [], columns);
  const today = todayKyiv();

  const doMove = (taskId: string, statusId: string) => {
    const task = tasks.data?.find((x) => x.id === taskId);
    if (!task || task.statusId === statusId) return;
    setMoveError(null);
    move.mutate(
      { id: taskId, statusId },
      { onError: (e) => setMoveError(t('tasks.board.moveFailed', { reason: t(taskErrorKey(e)) })) },
    );
  };

  return (
    <Stack gap="md">
      <Group justify="space-between" align="center" gap="sm">
        <Title order={1} size="h2">
          {t('tasks.board.title')}
        </Title>
        <Anchor component={Link} to={spaceId ? `/tasks?space=${spaceId}` : '/tasks'} size="sm">
          {t('tasks.board.list')}
        </Anchor>
      </Group>

      <Select
        aria-label={t('tasks.board.space')}
        placeholder={t('tasks.board.pickSpace')}
        data={(spaces.data ?? []).map((s) => ({ value: s.id, label: s.name }))}
        value={spaceId ?? null}
        onChange={(v) => {
          setMoveError(null);
          setParams(v ? { space: v } : {});
        }}
        size="md"
        searchable
      />

      {!spaceId && <Text c="dimmed">{t('tasks.board.pickSpace')}</Text>}
      {(space.isLoading || tasks.isLoading) && spaceId && <Loader size="sm" />}
      {(space.isError || tasks.isError) && <Alert color="red">{t(taskErrorKey(space.error ?? tasks.error))}</Alert>}
      {moveError && (
        <Alert color="red" role="alert" withCloseButton onClose={() => setMoveError(null)}>
          {moveError}
        </Alert>
      )}

      {space.data && tasks.data && (
        <Box
          data-testid="board"
          style={{ display: 'flex', gap: 'var(--mantine-spacing-sm)', overflowX: 'auto', alignItems: 'flex-start', paddingBottom: 8 }}
        >
          {columns.map((col) => {
            const items = grouped.get(col.id) ?? [];
            return (
              <Paper
                key={col.id}
                withBorder
                p="xs"
                radius="md"
                component="section"
                aria-label={col.name}
                data-testid="board-column"
                onDragOver={(e) => {
                  if (e.dataTransfer.types.includes(DRAG_MIME)) {
                    e.preventDefault();
                    setDragOver(col.id);
                  }
                }}
                onDragLeave={() => setDragOver((cur) => (cur === col.id ? null : cur))}
                onDrop={(e) => {
                  e.preventDefault();
                  setDragOver(null);
                  const id = e.dataTransfer.getData(DRAG_MIME);
                  if (id) doMove(id, col.id);
                }}
                style={{
                  flex: '0 0 min(280px, 85vw)',
                  background: dragOver === col.id ? 'var(--mantine-primary-color-light)' : undefined,
                }}
              >
                <Group justify="space-between" mb="xs" wrap="nowrap">
                  <Text fw={700} size="sm" style={{ overflowWrap: 'anywhere' }}>
                    {col.name}
                  </Text>
                  <Badge variant="light" color="gray" size="sm" aria-label={t('tasks.board.count', { count: items.length })}>
                    {items.length}
                  </Badge>
                </Group>
                <Stack gap="xs">
                  {items.length === 0 && (
                    <Text size="xs" c="dimmed">
                      {t('tasks.board.empty')}
                    </Text>
                  )}
                  {items.map((task) => (
                    <BoardCard key={task.id} task={task} columns={columns} today={today} onMove={doMove} />
                  ))}
                </Stack>
              </Paper>
            );
          })}
        </Box>
      )}
    </Stack>
  );
}
