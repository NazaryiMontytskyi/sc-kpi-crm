import { Alert, Badge, Button, Card, Group, Loader, Modal, Select, Stack, Text, Textarea, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { formatKyivDateTime } from '../audit/kyivTime';
import { usePermission } from '../auth/CurrentUser';
import { activityLevelLabel, type ActivityLevel } from './activityLevelApi';
import type { Member } from './memberApi';
import { memberErrorKey } from './memberProblems';
import { useActivityLevelHistory, useActivityLevels, useSetActivityLevel } from './useActivityLevels';

const TOUCH = 44;
const ACTIVITY_SET = 'members.activity.set';
const COMMENT_MAX = 1000;

function SetLevelModal({ member, onClose }: { member: Member; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const levels = useActivityLevels();
  const mutation = useSetActivityLevel(member.id);
  const [levelId, setLevelId] = useState<string | null>(null);
  const [comment, setComment] = useState('');
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const current = member.activityLevel?.id ?? null;
  const options = (levels.data ?? [])
    .filter((l) => l.id !== current)
    .map((l) => ({ value: l.id, label: activityLevelLabel(l, i18n.language) }));

  const submit = () => {
    if (!levelId) return;
    setErrorKey(null);
    mutation.mutate({ levelId, comment }, { onSuccess: onClose, onError: (e) => setErrorKey(memberErrorKey(e)) });
  };

  return (
    <Modal opened onClose={onClose} centered title={t('members.activity.setTitle')}>
      <Stack gap="md">
        {errorKey && <Alert color="red">{t(errorKey)}</Alert>}
        <Select
          label={t('members.activity.level')}
          placeholder={t('members.activity.choose')}
          data={options}
          value={levelId}
          onChange={setLevelId}
          allowDeselect={false}
          styles={{ input: { minHeight: TOUCH } }}
        />
        <Textarea
          label={t('members.activity.comment')}
          value={comment}
          onChange={(e) => setComment(e.currentTarget.value)}
          maxLength={COMMENT_MAX}
          minRows={2}
        />
        <Group justify="flex-end">
          <Button variant="default" mih={TOUCH} onClick={onClose}>
            {t('members.cancel')}
          </Button>
          <Button mih={TOUCH} disabled={!levelId} loading={mutation.isPending} onClick={submit}>
            {t('members.activity.save')}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function LevelBadge({ level }: { level: ActivityLevel | null }) {
  const { t, i18n } = useTranslation();
  if (!level) return <Text span c="dimmed">{t('members.activity.none')}</Text>;
  return (
    <Badge variant="light" size="lg">
      {activityLevelLabel(level, i18n.language)}
    </Badge>
  );
}

/** Current activity level (HR) with the change history; setting needs members.activity.set (backend enforces). */
export function MemberActivitySection({ member }: { member: Member }) {
  const { t, i18n } = useTranslation();
  const canSet = usePermission(ACTIVITY_SET);
  const history = useActivityLevelHistory(member.id);
  const [open, setOpen] = useState(false);

  return (
    <Card withBorder padding="md" data-testid="activity-section">
      <Stack gap="sm">
        <Group justify="space-between" align="center" wrap="wrap">
          <Title order={2} size="h4">
            {t('members.activity.title')}
          </Title>
          {canSet && !member.archived && (
            <Button mih={TOUCH} variant="default" onClick={() => setOpen(true)}>
              {t('members.activity.set')}
            </Button>
          )}
        </Group>
        <div>
          <Text size="sm" c="dimmed">
            {t('members.activity.current')}
          </Text>
          <LevelBadge level={member.activityLevel} />
        </div>
        <Text size="sm" fw={500}>
          {t('members.activity.history')}
        </Text>
        {history.isLoading && <Loader size="sm" />}
        {history.isError && <Alert color="red">{t(memberErrorKey(history.error))}</Alert>}
        {history.data && history.data.length === 0 && (
          <Text size="sm" c="dimmed">
            {t('members.activity.historyEmpty')}
          </Text>
        )}
        {history.data?.map((c) => (
          <Stack key={c.id} gap={2} data-testid="activity-change">
            <Text size="sm" style={{ overflowWrap: 'anywhere' }}>
              {c.from ? activityLevelLabel(c.from, i18n.language) : t('members.activity.none')}
              {' → '}
              {c.to ? activityLevelLabel(c.to, i18n.language) : '—'}
            </Text>
            <Text size="xs" c="dimmed">
              {formatKyivDateTime(c.changedAt, i18n.language)}
              {c.changedByLogin ? ` · ${c.changedByLogin}` : ''}
            </Text>
            {c.comment && (
              <Text size="sm" style={{ overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>
                {c.comment}
              </Text>
            )}
          </Stack>
        ))}
      </Stack>
      {open && <SetLevelModal member={member} onClose={() => setOpen(false)} />}
    </Card>
  );
}
