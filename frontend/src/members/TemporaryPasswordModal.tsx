import { Alert, Button, Group, Modal, Stack, Text, TextInput } from '@mantine/core';
import { useClipboard } from '@mantine/hooks';
import { useTranslation } from 'react-i18next';

const TOUCH = 44;

interface Props {
  /** One-time password. Held only in the caller's component state while this dialog is open. */
  password: string;
  /** Who the password belongs to (display name). */
  memberName: string;
  onClose: () => void;
}

/**
 * Shows a temporary password exactly once, with a copy button. It can only be dismissed with the explicit
 * button, so it is not lost by an accidental click; nothing is persisted or logged.
 */
export function TemporaryPasswordModal({ password, memberName, onClose }: Props) {
  const { t } = useTranslation();
  const clipboard = useClipboard({ timeout: 2000 });
  return (
    <Modal
      opened
      onClose={onClose}
      centered
      title={t('members.password.title')}
      closeOnClickOutside={false}
      closeOnEscape={false}
      withCloseButton={false}
    >
      <Stack gap="md">
        <Text>{t('members.password.intro', { name: memberName })}</Text>
        <Alert color="yellow">{t('members.password.once')}</Alert>
        <TextInput
          label={t('members.password.label')}
          value={password}
          readOnly
          onFocus={(e) => e.currentTarget.select()}
          autoComplete="off"
          styles={{ input: { minHeight: TOUCH, fontFamily: 'monospace' } }}
          data-autofocus
        />
        <Group justify="space-between" wrap="wrap">
          <Button variant="default" mih={TOUCH} onClick={() => clipboard.copy(password)}>
            {clipboard.copied ? t('members.password.copied') : t('members.password.copy')}
          </Button>
          <Button mih={TOUCH} onClick={onClose}>
            {t('members.password.done')}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
