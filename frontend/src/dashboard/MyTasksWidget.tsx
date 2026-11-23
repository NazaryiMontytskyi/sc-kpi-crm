import { Alert, Anchor, Card, Group, Loader, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { MyTasksOverview } from '../tasks/MyTasksOverview';
import { TASKS_VIEW } from '../tasks/taskLogic';
import { DASHBOARD_VIEW } from './dashboardApi';
import { isCounterHighlighted, WIDGET_COUNTERS, WIDGET_LINKS, WIDGET_TASKS_PER_GROUP, type WidgetCounter } from './dashboardLogic';
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
        {t(`dashboard.myTasks.${counter}`)}
      </Text>
    </Card>
  );
}

/**
 * Main-page "My tasks" widget (spec UI-4, RR-5): "My metrics" counters (open and overdue tasks I am an active assignee
 * of) and the "My tasks" overview grouped by deadline, a few tasks per group; a task opens in the tracker panel.
 */
export function MyTasksWidget() {
  const { t } = useTranslation();
  const allowed = usePermission(DASHBOARD_VIEW);
  const canSeeTasks = usePermission(TASKS_VIEW);
  const metrics = useMyDashboard(allowed);
  if (!allowed) return null;
  return (
    <Stack gap="sm" data-testid="my-tasks-widget">
      <Group justify="space-between" align="center">
        <Title order={2} size="h4">
          {t('dashboard.myTasks.title')}
        </Title>
        {canSeeTasks && (
          <Anchor component={Link} to="/tasks" style={{ minHeight: TOUCH, display: 'inline-flex', alignItems: 'center' }}>
            {t('dashboard.myTasks.openAll')}
          </Anchor>
        )}
      </Group>
      {metrics.isLoading && <Loader size="sm" />}
      {metrics.isError && <Alert color="red">{t('dashboard.myTasks.loadFailed')}</Alert>}
      {metrics.data && (
        <SimpleGrid cols={{ base: 2 }} spacing="xs">
          {WIDGET_COUNTERS.map((c) => (
            <Counter key={c} counter={c} value={metrics.data[c]} />
          ))}
        </SimpleGrid>
      )}
      {canSeeTasks && <MyTasksOverview limitPerGroup={WIDGET_TASKS_PER_GROUP} headingOrder={3} />}
    </Stack>
  );
}
