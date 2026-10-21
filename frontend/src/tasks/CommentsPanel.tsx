import { Alert, Button, Card, Group, Loader, Paper, Stack, Text, TextInput } from '@mantine/core';
import type { Editor } from '@tiptap/core';
import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { formatKyivDateTime } from '../audit/kyivTime';
import { useAuth } from '../auth/CurrentUser';
import type { MemberSummary } from '../members/memberApi';
import { fullName } from '../members/memberFormat';
import { useMembers } from '../members/useMembers';
import { isDocEmpty, RichTextField, RichTextView, type RichDoc } from '../shared/RichTextField';
import { AttachmentList, AttachmentUploader } from './Attachments';
import { remainingSlots } from './attachmentLogic';
import {
  canModifyComment,
  canSubmitComment,
  commentErrorKey,
  extractMentionIds,
  filterMentionCandidates,
  MAX_COMMENT_ATTACHMENTS,
} from './commentLogic';
import { uploadCommentFile, type Comment, type CommentAttachment, type CommentInput, type CommentTargetType } from './commentsApi';
import { useCommentMutations, useComments } from './useComments';

const TOUCH = 44;

interface ComposerProps {
  /** Initial content when editing an existing comment. */
  initial?: { body: RichDoc; attachments: CommentAttachment[] };
  members: readonly MemberSummary[];
  submitLabel: string;
  pending: boolean;
  onSubmit: (input: CommentInput) => Promise<unknown>;
  onCancel?: () => void;
  /** Clears the field after a successful send (new comments); editing closes the composer instead. */
  clearOnSuccess?: boolean;
}

/** Rich-text field with a member picker for @mentions, file attachments and a send button. */
function CommentComposer({ initial, members, submitLabel, pending, onSubmit, onCancel, clearOnSuccess }: ComposerProps) {
  const { t } = useTranslation();
  const [doc, setDoc] = useState<RichDoc | undefined>(initial && !isDocEmpty(initial.body) ? initial.body : undefined);
  const [attachments, setAttachments] = useState<CommentAttachment[]>(initial?.attachments ?? []);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [resetKey, setResetKey] = useState(0);
  const [picker, setPicker] = useState(false);
  const [search, setSearch] = useState('');
  const [uploading, setUploading] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const handleEditor = useCallback((e: Editor | null) => setEditor(e), []);

  const candidates = filterMentionCandidates(members, search);
  const insertMention = (m: MemberSummary) => {
    editor
      ?.chain()
      .focus()
      .insertContent([
        { type: 'mention', attrs: { id: m.id, label: fullName(m) } },
        { type: 'text', text: ' ' },
      ])
      .run();
    setPicker(false);
    setSearch('');
  };

  const canSend = canSubmitComment(isDocEmpty(doc), attachments.length) && !pending && !uploading;
  const send = async () => {
    setFailure(null);
    const body: RichDoc = doc ?? { type: 'doc', content: [{ type: 'paragraph' }] };
    try {
      await onSubmit({ body, mentionUserIds: extractMentionIds(body), attachmentIds: attachments.map((a) => a.id) });
      if (clearOnSuccess) {
        setDoc(undefined);
        setAttachments([]);
        setResetKey((k) => k + 1);
      }
    } catch (e) {
      setFailure(e);
    }
  };

  return (
    <Stack gap="xs">
      <RichTextField
        key={resetKey}
        label={t('tasks.comments.composerLabel')}
        value={doc}
        onChange={setDoc}
        mentions
        onEditor={handleEditor}
        minHeight={72}
        disabled={pending}
      />
      <AttachmentList
        attachments={attachments}
        label={t('tasks.comments.attachments')}
        removeLabel={(name) => t('tasks.comments.removeAttachment', { name })}
        onRemove={(a) => setAttachments((current) => current.filter((x) => x.id !== a.id))}
        disabled={pending}
      />
      {picker && (
        <Paper withBorder p="xs" aria-label={t('tasks.comments.mentionTitle')}>
          <Stack gap="xs">
            <TextInput
              aria-label={t('tasks.comments.mentionSearch')}
              placeholder={t('tasks.comments.mentionSearch')}
              value={search}
              onChange={(e) => setSearch(e.currentTarget.value)}
              size="md"
              data-autofocus
            />
            {candidates.length === 0 && (
              <Text size="sm" c="dimmed">
                {t('tasks.comments.mentionNone')}
              </Text>
            )}
            {candidates.map((m) => (
              <Button key={m.id} variant="subtle" justify="flex-start" mih={TOUCH} onClick={() => insertMention(m)}>
                {fullName(m)}
              </Button>
            ))}
          </Stack>
        </Paper>
      )}
      <AttachmentUploader
        upload={uploadCommentFile}
        slots={remainingSlots(attachments.length, MAX_COMMENT_ATTACHMENTS)}
        disabled={pending}
        onBusyChange={setUploading}
        onUploaded={(uploaded) => setAttachments((current) => [...current, ...uploaded])}
      />
      {failure !== null && <Alert color="red">{t(commentErrorKey(failure))}</Alert>}
      <Group gap="xs">
        <Button
          mih={TOUCH}
          variant={picker ? 'light' : 'default'}
          aria-expanded={picker}
          disabled={pending}
          onClick={() => setPicker((open) => !open)}
        >
          @ {t('tasks.comments.mention')}
        </Button>
        <Group gap="xs" ml="auto">
          {onCancel && (
            <Button mih={TOUCH} variant="default" onClick={onCancel} disabled={pending}>
              {t('tasks.comments.cancelEdit')}
            </Button>
          )}
          <Button mih={TOUCH} onClick={send} disabled={!canSend} loading={pending}>
            {submitLabel}
          </Button>
        </Group>
      </Group>
    </Stack>
  );
}

interface ItemProps {
  comment: Comment;
  members: readonly MemberSummary[];
  targetType: CommentTargetType;
  targetId: string;
}

function CommentItem({ comment, members, targetType, targetId }: ItemProps) {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();
  const { edit, archive } = useCommentMutations(targetType, targetId);
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const author = members.find((m) => m.id === comment.authorId);
  const mine = canModifyComment(user, comment);

  return (
    <Card withBorder padding="sm" data-testid="comment">
      <Stack gap="xs">
        <Group justify="space-between" gap="xs" align="flex-start">
          <Text fw={600} style={{ overflowWrap: 'anywhere' }}>
            {author ? fullName(author) : t('tasks.comments.unknownAuthor')}
          </Text>
          <Text size="xs" c="dimmed">
            {formatKyivDateTime(comment.createdAt, i18n.language)}
            {comment.edited && ` · ${t('tasks.comments.edited')}`}
          </Text>
        </Group>
        {editing ? (
          <CommentComposer
            initial={{ body: comment.body, attachments: comment.attachments }}
            members={members.filter((m) => !m.archived)}
            submitLabel={t('tasks.comments.saveEdit')}
            pending={edit.isPending}
            onCancel={() => setEditing(false)}
            onSubmit={async (input) => {
              await edit.mutateAsync({ id: comment.id, input });
              setEditing(false);
            }}
          />
        ) : (
          <>
            <div style={{ overflowWrap: 'anywhere' }}>
              <RichTextView key={JSON.stringify(comment.body)} value={comment.body} />
            </div>
            <AttachmentList attachments={comment.attachments} label={t('tasks.comments.attachments')} />
          </>
        )}
        {failure !== null && <Alert color="red">{t(commentErrorKey(failure))}</Alert>}
        {mine && !editing && (
          <Group gap="xs">
            <Button size="compact-md" mih={TOUCH} variant="subtle" onClick={() => setEditing(true)}>
              {t('tasks.comments.edit')}
            </Button>
            {!confirming && (
              <Button size="compact-md" mih={TOUCH} variant="subtle" color="red" onClick={() => setConfirming(true)}>
                {t('tasks.comments.delete')}
              </Button>
            )}
          </Group>
        )}
        {confirming && (
          <Alert color="red" role="alertdialog">
            <Stack gap="xs">
              <Text size="sm">{t('tasks.comments.deleteConfirm')}</Text>
              <Group gap="xs">
                <Button
                  mih={TOUCH}
                  color="red"
                  loading={archive.isPending}
                  onClick={() => {
                    setFailure(null);
                    archive.mutate(comment.id, { onError: setFailure, onSettled: () => setConfirming(false) });
                  }}
                >
                  {t('tasks.comments.delete')}
                </Button>
                <Button mih={TOUCH} variant="default" onClick={() => setConfirming(false)}>
                  {t('tasks.comments.cancelEdit')}
                </Button>
              </Group>
            </Stack>
          </Alert>
        )}
      </Stack>
    </Card>
  );
}

/** Comment thread of a record: oldest first, add / edit / delete (archive) with @mentions and attachments. */
export function CommentsPanel({ targetType, targetId, readOnly = false }: { targetType: CommentTargetType; targetId: string; readOnly?: boolean }) {
  const { t } = useTranslation();
  const members = useMembers(true);
  const thread = useComments(targetType, targetId);
  const { create } = useCommentMutations(targetType, targetId);
  const everyone = members.data ?? [];
  const comments = (thread.data?.pages ?? []).flatMap((p) => p.items);

  return (
    <Stack gap="sm">
      {thread.isLoading && <Loader size="sm" />}
      {thread.isError && <Alert color="red">{t('tasks.comments.loadFailed')}</Alert>}
      {thread.isSuccess && comments.length === 0 && <Text c="dimmed">{t('tasks.comments.empty')}</Text>}
      {comments.map((c) => (
        <CommentItem key={c.id} comment={c} members={everyone} targetType={targetType} targetId={targetId} />
      ))}
      {thread.hasNextPage && (
        <Button mih={TOUCH} variant="default" loading={thread.isFetchingNextPage} onClick={() => thread.fetchNextPage()}>
          {t('tasks.comments.loadMore')}
        </Button>
      )}
      {!readOnly && (
        <Card withBorder padding="sm">
          <CommentComposer
            members={everyone.filter((m) => !m.archived)}
            submitLabel={t('tasks.comments.send')}
            pending={create.isPending}
            clearOnSuccess
            onSubmit={(input) => create.mutateAsync(input)}
          />
        </Card>
      )}
    </Stack>
  );
}
