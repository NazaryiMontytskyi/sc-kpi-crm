import { ActionIcon, Alert, Anchor, Badge, Box, Button, Card, Group, Paper, Stack, Text, TextInput, Title } from '@mantine/core';
import { useState, type FormEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { formatKyivDateTime } from '../audit/kyivTime';
import { useAuth } from '../auth/CurrentUser';
import type { MemberSummary } from '../members/memberApi';
import { fileUrl } from '../members/memberApi';
import { fullName } from '../members/memberFormat';
import { AttachmentUploader } from './Attachments';
import { formatFileSize, type AttachmentInfo } from './attachmentLogic';
import { uploadAttachment } from './attachmentsApi';
import { artifactErrorKey, canManageArtifacts, linkTitleError, linkUrlError } from './panelLogic';
import { isSafeHttpUrl } from './taskLogic';
import type { Task, TaskArtifact } from './tasksApi';
import { useAddArtifact, useRemoveArtifact } from './useTasks';

const TOUCH = 44;
/** Files per pick; artifacts have no per-task limit of their own. */
const FILES_PER_PICK = 10;

interface ItemProps {
  artifact: TaskArtifact;
  members: readonly MemberSummary[];
  onRemove?: () => void;
  busy: boolean;
}

/** One artifact: name (opens in a new tab), type, who added it and when (AR-5), optional remove button. */
function ArtifactItem({ artifact, members, onRemove, busy }: ItemProps) {
  const { t, i18n } = useTranslation();
  const linkStyle = { overflowWrap: 'anywhere' as const, display: 'inline-block', minHeight: TOUCH, paddingBlock: 10 };
  const label = artifact.name || artifact.url || t('tasks.panel.fileUnavailable');
  let main: ReactNode;
  if (artifact.type === 'FILE' && artifact.fileId && artifact.fileAvailable) {
    main = (
      <Anchor href={fileUrl(artifact.fileId)} target="_blank" rel="noopener noreferrer" size="sm" style={linkStyle}>
        {label}
      </Anchor>
    );
  } else if (artifact.type === 'LINK' && isSafeHttpUrl(artifact.url)) {
    main = (
      <Anchor href={artifact.url ?? undefined} target="_blank" rel="noopener noreferrer" size="sm" style={linkStyle}>
        {label}
      </Anchor>
    );
  } else {
    main = (
      <Text size="sm" style={{ overflowWrap: 'anywhere' }}>
        {label}
      </Text>
    );
  }
  const adder = members.find((m) => m.id === artifact.addedBy);
  return (
    <Paper withBorder p="xs" role="listitem" data-testid="panel-artifact">
      <Group gap="xs" wrap="nowrap" justify="space-between" align="center">
        <Box style={{ minWidth: 0, flex: 1 }}>{main}</Box>
        <Badge variant="light" color="gray" size="sm" style={{ flexShrink: 0 }}>
          {t(`tasks.panel.artifactType.${artifact.type}`)}
        </Badge>
        {onRemove && (
          <ActionIcon
            variant="subtle"
            color="gray"
            size={TOUCH}
            disabled={busy}
            aria-label={t('tasks.artifacts.remove', { name: label })}
            onClick={onRemove}
            style={{ flexShrink: 0 }}
          >
            <span aria-hidden>×</span>
          </ActionIcon>
        )}
      </Group>
      {artifact.type === 'FILE' && !artifact.fileAvailable && (
        <Text size="xs" c="dimmed">
          {t('tasks.panel.fileUnavailable')}
        </Text>
      )}
      {artifact.type === 'FILE' && artifact.size !== null && artifact.fileAvailable && (
        <Text size="xs" c="dimmed">
          {formatFileSize(artifact.size)}
        </Text>
      )}
      {artifact.addedAt && (
        <Text size="xs" c="dimmed" data-testid="artifact-added">
          {t('tasks.artifacts.added', {
            name: adder ? fullName(adder) : t('tasks.panel.unknownMember'),
            date: formatKyivDateTime(artifact.addedAt, i18n.language),
          })}
        </Text>
      )}
    </Paper>
  );
}

/** Inline link form; the URL is validated before anything is sent (AR-2). */
function LinkForm({ onSubmit, busy, onCancel }: { onSubmit: (url: string, title: string) => Promise<unknown>; busy: boolean; onCancel: () => void }) {
  const { t } = useTranslation();
  const [url, setUrl] = useState('');
  const [title, setTitle] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const urlError = submitted ? linkUrlError(url) : null;
  const titleError = submitted ? linkTitleError(title) : null;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (linkUrlError(url) || linkTitleError(title)) return;
    try {
      await onSubmit(url.trim(), title.trim());
      setUrl('');
      setTitle('');
      setSubmitted(false);
    } catch {
      // The owner shows the failure; the form keeps its values for another try.
    }
  };

  return (
    <form onSubmit={submit} noValidate data-testid="artifact-link-form">
      <Stack gap="xs">
        <TextInput
          type="url"
          inputMode="url"
          label={t('tasks.artifacts.url')}
          placeholder={t('tasks.artifacts.urlPlaceholder')}
          value={url}
          onChange={(e) => setUrl(e.currentTarget.value)}
          error={urlError ? t(urlError) : undefined}
          required
          size="md"
        />
        <TextInput
          label={t('tasks.artifacts.linkTitle')}
          value={title}
          onChange={(e) => setTitle(e.currentTarget.value)}
          error={titleError ? t(titleError) : undefined}
          size="md"
        />
        <Group gap="xs">
          <Button type="submit" mih={TOUCH} loading={busy}>
            {t('tasks.artifacts.saveLink')}
          </Button>
          <Button variant="default" mih={TOUCH} disabled={busy} onClick={onCancel}>
            {t('tasks.cancel')}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

/**
 * Artifacts of a task (spec AR-1..AR-5): files and links listed with name, type, adder and time. The creator,
 * assignees and `tasks.edit.any` can attach files (uploaded to the files module first), attach links and remove
 * artifacts; removal archives the record and the file stays in storage.
 */
export function PanelArtifacts({ task, members }: { task: Task; members: readonly MemberSummary[] }) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const add = useAddArtifact();
  const remove = useRemoveArtifact();
  const [failure, setFailure] = useState<unknown>(null);
  const [linkOpen, setLinkOpen] = useState(false);
  const manage = canManageArtifacts(user, task);
  const busy = add.isPending || remove.isPending;
  const active = task.artifacts.filter((a) => !a.archived);

  const attachFiles = async (uploaded: AttachmentInfo[]) => {
    setFailure(null);
    for (const file of uploaded) {
      try {
        await add.mutateAsync({ taskId: task.id, input: { type: 'FILE', fileId: file.id } });
      } catch (e) {
        setFailure(e);
      }
    }
  };

  const attachLink = async (url: string, title: string) => {
    setFailure(null);
    try {
      await add.mutateAsync({ taskId: task.id, input: { type: 'LINK', url, title: title || undefined } });
      setLinkOpen(false);
    } catch (e) {
      setFailure(e);
      throw e;
    }
  };

  return (
    <Card withBorder padding="sm" component="section" aria-labelledby="task-panel-artifacts">
      <Title order={3} size="h5" mb="xs" id="task-panel-artifacts">
        {t('tasks.panel.artifacts')}
      </Title>
      <Stack gap="xs">
        {active.length === 0 ? (
          <Text c="dimmed" size="sm">
            {t('tasks.panel.noArtifacts')}
          </Text>
        ) : (
          <Stack gap="xs" role="list" aria-label={t('tasks.panel.artifacts')}>
            {active.map((a) => (
              <ArtifactItem
                key={a.id}
                artifact={a}
                members={members}
                busy={busy}
                onRemove={
                  manage
                    ? () => {
                        setFailure(null);
                        remove.mutate({ taskId: task.id, artifactId: a.id }, { onError: setFailure });
                      }
                    : undefined
                }
              />
            ))}
          </Stack>
        )}
        {failure !== null && (
          <Alert color="red" role="alert" withCloseButton closeButtonLabel={t('tasks.close')} onClose={() => setFailure(null)}>
            {t(artifactErrorKey(failure))}
          </Alert>
        )}
        {manage && (
          <>
            <AttachmentUploader
              upload={uploadAttachment}
              slots={FILES_PER_PICK}
              disabled={busy}
              onUploaded={attachFiles}
              labels={{ add: t('tasks.artifacts.addFile'), takePhoto: t('tasks.artifacts.takePhoto') }}
            />
            {linkOpen ? (
              <LinkForm onSubmit={attachLink} busy={add.isPending} onCancel={() => setLinkOpen(false)} />
            ) : (
              <Group>
                <Button variant="default" mih={TOUCH} onClick={() => setLinkOpen(true)}>
                  {t('tasks.artifacts.addLink')}
                </Button>
              </Group>
            )}
          </>
        )}
      </Stack>
    </Card>
  );
}
