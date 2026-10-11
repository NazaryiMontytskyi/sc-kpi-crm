import { Alert, Button, Group, Modal, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Member } from './memberApi';
import { fullName } from './memberFormat';
import { memberErrorKey } from './memberProblems';
import { useMemberMutations } from './useMembers';

const TOUCH = 44;

export type MemberAction = 'archive' | 'restore' | 'reset';

interface Props {
  action: MemberAction;
  member: Member;
  onClose: () => void;
  /** Called with the one-time temporary password after a successful password reset. */
  onPasswordReset?: (temporaryPassword: string) => void;
}

/** Confirmation for archiving, restoring and resetting the password of a member. */
export function MemberActionModal({ action, member, onClose, onPasswordReset }: Props) {
  const { t } = useTranslation();
  const mutations = useMemberMutations();
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const pending = mutations.archive.isPending || mutations.restore.isPending || mutations.resetPassword.isPending;
  const name = fullName(member);

  const confirm = () => {
    setErrorKey(null);
    const onError = (error: unknown) => setErrorKey(memberErrorKey(error));
    if (action === 'archive') mutations.archive.mutate(member.id, { onSuccess: onClose, onError });
    else if (action === 'restore') mutations.restore.mutate(member.id, { onSuccess: onClose, onError });
    else {
      mutations.resetPassword.mutate(member.id, {
        onSuccess: (password) => {
          // Hand the password to the caller's dialog and drop it from the mutation cache.
          mutations.resetPassword.reset();
          onPasswordReset?.(password);
        },
        onError,
      });
    }
  };

  return (
    <Modal opened onClose={onClose} centered title={t(`members.${action}.title`)}>
      <Stack gap="md">
        {errorKey && <Alert color="red">{t(errorKey)}</Alert>}
        <Text>{t(`members.${action}.confirm`, { name })}</Text>
        {action === 'archive' && <Alert color="yellow">{t('members.archive.handoverHint')}</Alert>}
        <Group justify="flex-end">
          <Button variant="default" mih={TOUCH} onClick={onClose}>
            {t('members.cancel')}
          </Button>
          <Button color={action === 'restore' ? undefined : 'red'} mih={TOUCH} loading={pending} onClick={confirm}>
            {t(`members.${action}.submit`)}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
