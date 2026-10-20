import { Alert, Badge, Button, Card, Group, Loader, Stack, Text, Timeline } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { formatKyivDateTime } from '../audit/kyivTime';
import { useAuth } from '../auth/CurrentUser';
import type { MemberSummary } from '../members/memberApi';
import { formatDate, fullName } from '../members/memberFormat';
import { useMembers } from '../members/useMembers';
import { NoteDialog, ResolutionFormDialog } from './ResolutionDialogs';
import {
  availableActions,
  canIssueResolution,
  resolutionErrorKey,
  resolutionWarningKey,
  STATE_COLORS,
  type ResolutionAction,
} from './resolutionLogic';
import type { Resolution, ResolutionState } from './resolutionsApi';
import { useResolution, useResolutionMutations, useTaskResolutions } from './useResolutions';

const TOUCH = 44;

type DialogKind = 'submit' | 'rework' | 'cancel' | 'redirect';
interface OpenDialog {
  kind: DialogKind;
  resolution: Resolution;
}

export function StateBadge({ state }: { state: ResolutionState }) {
  const { t } = useTranslation();
  return (
    <Badge color={STATE_COLORS[state]} variant={state === 'CANCELED' ? 'outline' : 'light'}>
      {t(`tasks.resolutions.state.${state}`)}
    </Badge>
  );
}

function nameOf(members: readonly MemberSummary[], id: string, fallback: string): string {
  const m = members.find((x) => x.id === id);
  return m ? fullName(m) : fallback;
}

function HistoryTimeline({ resolutionId, members }: { resolutionId: string; members: readonly MemberSummary[] }) {
  const { t, i18n } = useTranslation();
  const detail = useResolution(resolutionId, true);
  if (detail.isLoading) return <Loader size="sm" />;
  if (detail.isError || !detail.data) return <Alert color="red">{t('tasks.resolutions.loadFailed')}</Alert>;
  const { completionNote, history } = detail.data;
  return (
    <Stack gap="xs">
      {completionNote && (
        <div>
          <Text size="xs" c="dimmed">
            {t('tasks.resolutions.completionNote')}
          </Text>
          <Text size="sm" style={{ overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>
            {completionNote}
          </Text>
        </div>
      )}
      <Text size="xs" c="dimmed">
        {t('tasks.resolutions.history')}
      </Text>
      <Timeline bulletSize={16} lineWidth={2} active={history.length}>
        {history.map((h, i) => (
          <Timeline.Item
            key={`${h.at}-${i}`}
            color={STATE_COLORS[h.to]}
            title={<Text size="sm">{t(`tasks.resolutions.state.${h.to}`)}</Text>}
          >
            <Text size="xs" c="dimmed">
              {nameOf(members, h.actorId, t('tasks.comments.unknownAuthor'))} · {formatKyivDateTime(h.at, i18n.language)}
            </Text>
            {h.comment && (
              <Text size="sm" style={{ overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>
                {h.comment}
              </Text>
            )}
          </Timeline.Item>
        ))}
      </Timeline>
    </Stack>
  );
}

interface CardProps {
  resolution: Resolution;
  members: readonly MemberSummary[];
  onDialog: (d: OpenDialog) => void;
  onSimple: (id: string, action: 'start' | 'resume' | 'accept') => void;
  busy: boolean;
}

function ResolutionCard({ resolution: r, members, onDialog, onSimple, busy }: CardProps) {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const actions = availableActions(user, r);
  const unknown = t('tasks.comments.unknownAuthor');
  const overdue = r.overdue;

  const button = (action: ResolutionAction) => {
    const dialogKind = action === 'submit' || action === 'rework' || action === 'cancel' || action === 'redirect' ? action : null;
    return (
      <Button
        key={action}
        mih={TOUCH}
        variant={action === 'cancel' ? 'subtle' : action === 'redirect' || action === 'rework' ? 'default' : 'filled'}
        color={action === 'cancel' ? 'red' : undefined}
        disabled={busy}
        onClick={() => (dialogKind ? onDialog({ kind: dialogKind, resolution: r }) : onSimple(r.id, action as 'start' | 'resume' | 'accept'))}
      >
        {t(`tasks.resolutions.action.${action}`)}
      </Button>
    );
  };

  return (
    <Card withBorder padding="sm" data-testid="resolution" style={overdue ? { borderColor: 'var(--mantine-color-red-6)' } : undefined}>
      <Stack gap="xs">
        <Group justify="space-between" gap="xs" align="flex-start">
          <Group gap="xs">
            <StateBadge state={r.state} />
            {overdue && (
              <Badge color="red" variant="filled">
                {t('tasks.resolutions.overdue')}
              </Badge>
            )}
          </Group>
          <Text size="xs" c="dimmed">
            {formatKyivDateTime(r.createdAt, i18n.language)}
          </Text>
        </Group>
        <Text style={{ overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>{r.instruction}</Text>
        <Stack gap={2}>
          <Text size="sm">
            <Text span c="dimmed">
              {t('tasks.resolutions.fields.author')}:{' '}
            </Text>
            {nameOf(members, r.authorId, unknown)}
          </Text>
          <Text size="sm">
            <Text span c="dimmed">
              {t('tasks.resolutions.fields.executor')}:{' '}
            </Text>
            {nameOf(members, r.executorId, unknown)}
          </Text>
          {r.coExecutorIds.length > 0 && (
            <Text size="sm">
              <Text span c="dimmed">
                {t('tasks.resolutions.fields.coExecutors')}:{' '}
              </Text>
              {r.coExecutorIds.map((id) => nameOf(members, id, unknown)).join(', ')}
            </Text>
          )}
          <Text size="sm" c={overdue ? 'red' : undefined} fw={overdue ? 600 : undefined}>
            <Text span c={overdue ? 'red' : 'dimmed'}>
              {t('tasks.resolutions.fields.dueDate')}:{' '}
            </Text>
            {formatDate(r.dueDate)}
          </Text>
        </Stack>
        <Group gap="xs">
          {actions.map(button)}
          <Button mih={TOUCH} variant="subtle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
            {t(open ? 'tasks.resolutions.hideHistory' : 'tasks.resolutions.showHistory')}
          </Button>
        </Group>
        {open && <HistoryTimeline resolutionId={r.id} members={members} />}
      </Stack>
    </Card>
  );
}

/** Resolutions of a task: list, issue dialog, executor and author actions, history timeline. */
export function ResolutionsPanel({ taskId, taskArchived }: { taskId: string; taskArchived: boolean }) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const members = useMembers(true);
  const list = useTaskResolutions(taskId);
  const m = useResolutionMutations(taskId);
  const [dialog, setDialog] = useState<OpenDialog | 'issue' | null>(null);
  const [failure, setFailure] = useState<unknown>(null);
  const [warnings, setWarnings] = useState<string[]>([]);

  const everyone = members.data ?? [];
  const items = (list.data?.pages ?? []).flatMap((p) => p.items);
  const busy = m.simple.isPending;

  const close = () => {
    setDialog(null);
    setFailure(null);
  };
  const dialogHandlers = { onSuccess: close, onError: setFailure };

  const simple = (id: string, action: 'start' | 'resume' | 'accept') => {
    setFailure(null);
    setWarnings([]);
    m.simple.mutate({ id, action }, { onError: setFailure });
  };

  return (
    <Stack gap="sm">
      {canIssueResolution(user, taskArchived) && (
        <Group>
          <Button
            mih={TOUCH}
            onClick={() => {
              setFailure(null);
              setWarnings([]);
              setDialog('issue');
            }}
          >
            {t('tasks.resolutions.issue')}
          </Button>
        </Group>
      )}
      {dialog === null && failure !== null && <Alert color="red">{t(resolutionErrorKey(failure))}</Alert>}
      {warnings.map((w) => {
        const key = resolutionWarningKey(w);
        return key ? (
          <Alert key={w} color="yellow" withCloseButton onClose={() => setWarnings([])} closeButtonLabel={t('tasks.close')}>
            {t(key)}
          </Alert>
        ) : null;
      })}
      {list.isLoading && <Loader size="sm" />}
      {list.isError && <Alert color="red">{t('tasks.resolutions.loadFailed')}</Alert>}
      {list.isSuccess && items.length === 0 && <Text c="dimmed">{t('tasks.resolutions.empty')}</Text>}
      {items.map((r) => (
        <ResolutionCard
          key={r.id}
          resolution={r}
          members={everyone}
          busy={busy}
          onSimple={simple}
          onDialog={(d) => {
            setFailure(null);
            setWarnings([]);
            setDialog(d);
          }}
        />
      ))}
      {list.hasNextPage && (
        <Button mih={TOUCH} variant="default" loading={list.isFetchingNextPage} onClick={() => list.fetchNextPage()}>
          {t('tasks.comments.loadMore')}
        </Button>
      )}

      {dialog === 'issue' && (
        <ResolutionFormDialog
          mode="issue"
          pending={m.issue.isPending}
          failure={failure}
          onClose={close}
          onIssue={(input) => {
            setFailure(null);
            m.issue.mutate(input, dialogHandlers);
          }}
        />
      )}
      {dialog && dialog !== 'issue' && dialog.kind === 'redirect' && (
        <ResolutionFormDialog
          mode="redirect"
          excludeUserId={dialog.resolution.executorId}
          pending={m.redirect.isPending}
          failure={failure}
          onClose={close}
          onRedirect={(input) => {
            setFailure(null);
            m.redirect.mutate(
              { id: dialog.resolution.id, input },
              {
                onSuccess: (res) => {
                  setWarnings(res.warnings);
                  close();
                },
                onError: setFailure,
              },
            );
          }}
        />
      )}
      {dialog && dialog !== 'issue' && dialog.kind !== 'redirect' && (
        <NoteDialog
          kind={dialog.kind}
          pending={m.submit.isPending || m.rework.isPending || m.cancel.isPending}
          failure={failure}
          onClose={close}
          onConfirm={(text) => {
            setFailure(null);
            const id = dialog.resolution.id;
            if (dialog.kind === 'submit') m.submit.mutate({ id, note: text }, dialogHandlers);
            else if (dialog.kind === 'rework') m.rework.mutate({ id, comment: text }, dialogHandlers);
            else m.cancel.mutate({ id, comment: text || undefined }, dialogHandlers);
          }}
        />
      )}
    </Stack>
  );
}
