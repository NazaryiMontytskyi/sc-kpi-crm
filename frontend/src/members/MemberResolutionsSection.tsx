import { Alert, Card, Group, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { usePermission } from '../auth/CurrentUser';
import { isOverdueHighlighted } from '../tasks/memberResolutionLogic';
import { MemberResolutionList } from '../tasks/MemberResolutionList';
import { useMemberResolutionCounts, useMemberResolutions } from '../tasks/useMemberResolutions';

const TASKS_VIEW = 'tasks.view';

function Counter({ label, value, color, testId }: { label: string; value: number; color?: string; testId: string }) {
  return (
    <Card withBorder padding="sm" style={{ flex: '1 1 90px' }} data-testid={testId}>
      <Text size="xl" fw={700} c={color}>
        {value}
      </Text>
      <Text size="sm" c="dimmed">
        {label}
      </Text>
    </Card>
  );
}

/** Active resolutions and simple counters (active, overdue, accepted) of a member; visible to everyone with tasks.view. */
export function MemberResolutionsSection({ userId }: { userId: string }) {
  const { t } = useTranslation();
  const allowed = usePermission(TASKS_VIEW);
  const counts = useMemberResolutionCounts(userId, allowed);
  const list = useMemberResolutions(userId, { role: 'EXECUTOR', active: true }, allowed);
  if (!allowed) return null;

  return (
    <Stack gap="sm" data-testid="member-resolutions">
      <Title order={2} size="h3">
        {t('memberResolutions.profileTitle')}
      </Title>
      {counts.isError ? (
        <Alert color="red">{t('memberResolutions.loadFailed')}</Alert>
      ) : (
        <Group gap="xs" wrap="wrap">
          <Counter testId="count-active" label={t('memberResolutions.counts.active')} value={counts.data?.active ?? 0} />
          <Counter
            testId="count-overdue"
            label={t('memberResolutions.counts.overdue')}
            value={counts.data?.overdue ?? 0}
            color={isOverdueHighlighted(counts.data?.overdue ?? 0) ? 'red' : undefined}
          />
          <Counter testId="count-accepted" label={t('memberResolutions.counts.accepted')} value={counts.data?.accepted ?? 0} />
        </Group>
      )}
      <MemberResolutionList query={list} emptyKey="memberResolutions.emptyActive" />
    </Stack>
  );
}
