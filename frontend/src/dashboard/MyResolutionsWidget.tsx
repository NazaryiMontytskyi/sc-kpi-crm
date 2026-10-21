import { Alert, Anchor, Card, Group, Loader, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { formatDate } from '../members/memberFormat';
import { StateBadge } from '../tasks/StateBadge';
import { DASHBOARD_VIEW } from './dashboardApi';
import { isCounterHighlighted, WIDGET_COUNTERS, WIDGET_LINKS, type WidgetCounter } from './dashboardLogic';
import { useMyDashboard } from './useDashboard';

const TOUCH = 44;

function Counter({ counter, value }: { counter: WidgetCounter; value: number }) {
  const { t } = useTranslation();
  const hot = isCounterHighlighted(counter, value);
  return (
    <Card
      withBorder
      padding="sm"
      component={Link}
      to={WIDGET_LINKS[counter]}
      data-testid={`dashboard-counter-${counter}`}
      style={{ minHeight: TOUCH, textDecoration: 'none', ...(hot ? { borderColor: 'var(--mantine-color-red-6)' } : {}) }}
    >
      <Text size="xl" fw={700} c={hot ? 'red' : undefined} data-testid={`dashboard-value-${counter}`}>
        {value}
      </Text>
      <Text size="sm" c="dimmed">
        {t(`dashboard.myResolutions.${counter}`)}
      </Text>
    </Card>
  );
}

/** Home page widget: my resolutions at a glance (counts link to the filtered lists) and the nearest deadlines. */
export function MyResolutionsWidget() {
  const { t } = useTranslation();
  const allowed = usePermission(DASHBOARD_VIEW);
  const query = useMyDashboard(allowed);
  if (!allowed) return null;
  return (
    <Stack gap="sm" data-testid="my-resolutions-widget">
      <Group justify="space-between" align="center">
        <Title order={2} size="h4">
          {t('dashboard.myResolutions.title')}
        </Title>
        <Anchor component={Link} to="/resolutions/mine" style={{ minHeight: TOUCH, display: 'inline-flex', alignItems: 'center' }}>
          {t('dashboard.myResolutions.openAll')}
        </Anchor>
      </Group>
      {query.isLoading && <Loader size="sm" />}
      {query.isError && <Alert color="red">{t('dashboard.myResolutions.loadFailed')}</Alert>}
      {query.data && (
        <>
          <SimpleGrid cols={{ base: 1, xs: 3 }} spacing="xs">
            {WIDGET_COUNTERS.map((c) => (
              <Counter key={c} counter={c} value={query.data[c]} />
            ))}
          </SimpleGrid>
          <Title order={3} size="h5">
            {t('dashboard.myResolutions.upcoming')}
          </Title>
          {query.data.upcoming.length === 0 ? (
            <Text c="dimmed" size="sm">
              {t('dashboard.myResolutions.empty')}
            </Text>
          ) : (
            <Stack gap="xs">
              {query.data.upcoming.map((u) => (
                <Card key={u.resolutionId} withBorder padding="sm" data-testid="dashboard-upcoming">
                  <Group justify="space-between" wrap="wrap" gap="xs">
                    <Anchor
                      component={Link}
                      to={`/tasks/${u.taskId}`}
                      fw={600}
                      style={{ overflowWrap: 'anywhere', minHeight: TOUCH, display: 'inline-flex', alignItems: 'center' }}
                    >
                      {u.taskTitle || t('memberResolutions.untitledTask')}
                    </Anchor>
                    <Group gap="xs">
                      <StateBadge state={u.state} />
                      <Text size="sm" c={u.overdue ? 'red' : 'dimmed'} fw={u.overdue ? 600 : undefined}>
                        {formatDate(u.dueDate)}
                      </Text>
                    </Group>
                  </Group>
                </Card>
              ))}
            </Stack>
          )}
        </>
      )}
    </Stack>
  );
}
