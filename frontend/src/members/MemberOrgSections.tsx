import { Alert, Card, Loader, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { formatDate } from './memberFormat';
import { indexUnits, splitAppointments, splitMemberships } from './memberProfileOrg';
import { useMemberOrg } from './useMemberOrg';

function Period({ from, to }: { from: string; to: string | null }) {
  const { t } = useTranslation();
  return (
    <Text size="sm" c="dimmed">
      {t('members.profile.period', { from: formatDate(from), to: to ? formatDate(to) : t('members.profile.present') })}
    </Text>
  );
}

function Group({ title, empty, children }: { title: string; empty: boolean; children: React.ReactNode }) {
  if (empty) return null;
  return (
    <Stack gap={4}>
      <Text fw={500} size="sm">
        {title}
      </Text>
      {children}
    </Stack>
  );
}

function Placeholder({ title, text, testId }: { title: string; text: string; testId: string }) {
  return (
    <Card withBorder padding="md" data-testid={testId}>
      <Stack gap={4}>
        <Title order={2} size="h4">
          {title}
        </Title>
        <Text size="sm" c="dimmed">
          {text}
        </Text>
      </Stack>
    </Card>
  );
}

/**
 * Org-related sections of the member profile (INC-021): positions and unit memberships, current and past,
 * plus empty slots for tasks/resolutions (INC-037) and statistics (v2).
 */
export function MemberOrgSections({ userId }: { userId: string }) {
  const { t } = useTranslation();
  const org = useMemberOrg(userId);

  if (org.isLoading) return <Loader size="sm" />;
  if (org.isError) return <Alert color="red">{t('members.profile.loadFailed')}</Alert>;

  const names = indexUnits(org.tree.data ?? []).names;
  const unitName = (id: string) => names.get(id) ?? t('members.profile.unknownUnit');
  const positions = splitAppointments(org.appointments.data ?? []);
  const memberships = splitMemberships(org.memberships.data ?? []);

  const positionLine = (positionId: string) => {
    const p = org.positionsById.get(positionId);
    return p ? t('members.directory.positionOption', { title: p.title, unit: unitName(p.orgUnitId) }) : t('members.profile.unknownPosition');
  };

  return (
    <Stack gap="md">
      <Card withBorder padding="md" data-testid="profile-positions">
        <Stack gap="sm">
          <Title order={2} size="h4">
            {t('members.profile.positions')}
          </Title>
          {positions.current.length + positions.past.length === 0 && (
            <Text size="sm" c="dimmed">
              {t('members.profile.positionsEmpty')}
            </Text>
          )}
          <Group title={t('members.profile.positionsCurrent')} empty={positions.current.length === 0}>
            {positions.current.map((a) => (
              <div key={a.id}>
                <Text>{positionLine(a.positionId)}</Text>
                <Period from={a.startDate} to={a.endDate} />
              </div>
            ))}
          </Group>
          <Group title={t('members.profile.positionsPast')} empty={positions.past.length === 0}>
            {positions.past.map((a) => (
              <div key={a.id}>
                <Text>{positionLine(a.positionId)}</Text>
                <Period from={a.startDate} to={a.endDate} />
              </div>
            ))}
          </Group>
        </Stack>
      </Card>

      <Card withBorder padding="md" data-testid="profile-memberships">
        <Stack gap="sm">
          <Title order={2} size="h4">
            {t('members.profile.memberships')}
          </Title>
          {memberships.current.length + memberships.past.length === 0 && (
            <Text size="sm" c="dimmed">
              {t('members.profile.membershipsEmpty')}
            </Text>
          )}
          <Group title={t('members.profile.membershipsCurrent')} empty={memberships.current.length === 0}>
            {memberships.current.map((m) => (
              <div key={m.id}>
                <Text>{unitName(m.orgUnitId)}</Text>
                <Period from={m.since} to={m.until} />
              </div>
            ))}
          </Group>
          <Group title={t('members.profile.membershipsPast')} empty={memberships.past.length === 0}>
            {memberships.past.map((m) => (
              <div key={m.id}>
                <Text>{unitName(m.orgUnitId)}</Text>
                <Period from={m.since} to={m.until} />
              </div>
            ))}
          </Group>
        </Stack>
      </Card>

      <Placeholder title={t('members.profile.tasks')} text={t('members.profile.tasksEmpty')} testId="profile-tasks" />
      <Placeholder
        title={t('members.profile.resolutions')}
        text={t('members.profile.resolutionsEmpty')}
        testId="profile-resolutions"
      />
      <Placeholder
        title={t('members.profile.statistics')}
        text={t('members.profile.statisticsEmpty')}
        testId="profile-statistics"
      />
    </Stack>
  );
}
