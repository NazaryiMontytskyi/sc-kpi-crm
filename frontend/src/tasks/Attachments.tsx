import { ActionIcon, Alert, Anchor, Box, Button, FileButton, Group, Paper, Progress, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fileUrl } from '../members/memberApi';
import { ATTACH_ACCEPT, attachmentErrorKey, formatFileSize, isThumbnailable, type AttachmentInfo } from './attachmentLogic';

const TOUCH = 44;
const THUMB = 48;

function Thumbnail({ attachment }: { attachment: AttachmentInfo }) {
  const [broken, setBroken] = useState(false);
  if (!isThumbnailable(attachment.mimeType) || broken) return null;
  return (
    <Anchor
      href={fileUrl(attachment.id)}
      target="_blank"
      rel="noopener noreferrer"
      aria-hidden
      tabIndex={-1}
      style={{ display: 'block', flex: `0 0 ${THUMB}px` }}
    >
      <Box
        component="img"
        src={fileUrl(attachment.id)}
        alt=""
        loading="lazy"
        width={THUMB}
        height={THUMB}
        onError={() => setBroken(true)}
        style={{ objectFit: 'cover', borderRadius: 'var(--mantine-radius-sm)', display: 'block' }}
        data-testid="attachment-thumbnail"
      />
    </Anchor>
  );
}

interface ListProps {
  attachments: readonly AttachmentInfo[];
  label: string;
  /** When given, each item gets a remove button (44px target). */
  onRemove?: (attachment: AttachmentInfo) => void;
  removeLabel?: (name: string) => string;
  disabled?: boolean;
}

/** Attachments as rows: image thumbnail, download link with name and size, optional remove button. */
export function AttachmentList({ attachments, label, onRemove, removeLabel, disabled }: ListProps) {
  if (attachments.length === 0) return null;
  return (
    <Stack gap="xs" role="list" aria-label={label}>
      {attachments.map((a) => (
        <Paper key={a.id} withBorder p="xs" role="listitem">
          <Group gap="sm" wrap="nowrap" align="center">
            <Thumbnail attachment={a} />
            <Box style={{ flex: 1, minWidth: 0 }}>
              <Anchor
                href={fileUrl(a.id)}
                target="_blank"
                rel="noopener noreferrer"
                size="sm"
                style={{ overflowWrap: 'anywhere', display: 'inline-block', minHeight: TOUCH, lineHeight: `${TOUCH - 16}px`, paddingBlock: 8 }}
              >
                {a.fileName}
              </Anchor>
              <Text size="xs" c="dimmed">
                {formatFileSize(a.size)}
              </Text>
            </Box>
            {onRemove && (
              <ActionIcon
                variant="subtle"
                color="gray"
                size={TOUCH}
                disabled={disabled}
                aria-label={removeLabel ? removeLabel(a.fileName) : a.fileName}
                onClick={() => onRemove(a)}
              >
                <span aria-hidden>×</span>
              </ActionIcon>
            )}
          </Group>
        </Paper>
      ))}
    </Stack>
  );
}

interface Progressing {
  name: string;
  fraction: number;
}

interface UploaderProps {
  upload: (file: File, onProgress: (fraction: number) => void) => Promise<AttachmentInfo>;
  /** Called once per picked batch with the files that uploaded successfully. */
  onUploaded: (uploaded: AttachmentInfo[]) => void | Promise<unknown>;
  /** How many more files may be attached; extra picked files are skipped with a message. */
  slots: number;
  disabled?: boolean;
  /** Reports whether an upload is running, so the owner can block submitting meanwhile. */
  onBusyChange?: (busy: boolean) => void;
}

/**
 * File picker buttons (files / camera) with a progress bar per upload and localized rejection messages.
 * The files are uploaded one after another so that one rejected file does not lose the others.
 */
export function AttachmentUploader({ upload, onUploaded, slots, disabled, onBusyChange }: UploaderProps) {
  const { t } = useTranslation();
  const [active, setActive] = useState<Progressing | null>(null);
  const [errors, setErrors] = useState<Array<{ name: string; key: string }>>([]);
  const busy = active !== null;

  const run = async (picked: File[] | File | null) => {
    const files = picked === null ? [] : Array.isArray(picked) ? picked : [picked];
    if (files.length === 0) return;
    const failures: Array<{ name: string; key: string }> = [];
    const accepted = files.slice(0, slots);
    for (const skipped of files.slice(slots)) failures.push({ name: skipped.name, key: 'tasks.attachments.limitReached' });
    const uploaded: AttachmentInfo[] = [];
    setErrors([]);
    onBusyChange?.(true);
    for (const file of accepted) {
      setActive({ name: file.name, fraction: 0 });
      try {
        uploaded.push(await upload(file, (fraction) => setActive({ name: file.name, fraction })));
      } catch (e) {
        failures.push({ name: file.name, key: attachmentErrorKey(e) });
      }
    }
    setActive(null);
    onBusyChange?.(false);
    setErrors(failures);
    if (uploaded.length > 0) {
      try {
        await onUploaded(uploaded);
      } catch (e) {
        setErrors((current) => [...current, { name: '', key: attachmentErrorKey(e) }]);
      }
    }
  };

  const off = disabled || busy || slots <= 0;
  return (
    <Stack gap="xs">
      <Group gap="xs">
        <FileButton onChange={run} accept={ATTACH_ACCEPT} multiple disabled={off}>
          {(props) => (
            <Button {...props} mih={TOUCH} variant="default" loading={busy} disabled={off}>
              {t('tasks.attachments.add')}
            </Button>
          )}
        </FileButton>
        {/* On phones this opens the camera directly; desktop browsers fall back to the file dialog. */}
        <FileButton onChange={run} accept="image/*" capture="environment" disabled={off}>
          {(props) => (
            <Button {...props} mih={TOUCH} variant="default" disabled={off}>
              {t('tasks.attachments.takePhoto')}
            </Button>
          )}
        </FileButton>
      </Group>
      {slots <= 0 && (
        <Text size="xs" c="dimmed">
          {t('tasks.attachments.limitReached')}
        </Text>
      )}
      {active && (
        <div role="status" aria-live="polite">
          <Text size="xs" style={{ overflowWrap: 'anywhere' }}>
            {t('tasks.attachments.uploading', { name: active.name, percent: Math.round(active.fraction * 100) })}
          </Text>
          <Progress
            value={Math.round(active.fraction * 100)}
            size="sm"
            aria-label={t('tasks.attachments.progressLabel', { name: active.name })}
            animated={active.fraction < 1}
          />
        </div>
      )}
      {errors.map((e, i) => (
        <Alert key={`${e.name}-${i}`} color="red" role="alert">
          {e.name ? `${e.name}: ` : ''}
          {t(e.key)}
        </Alert>
      ))}
    </Stack>
  );
}
