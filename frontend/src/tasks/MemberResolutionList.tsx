import { Alert, Anchor, Badge, Button, Card, Group, Loader, Stack, Text } from '@mantine/core';
import type { UseInfiniteQueryResult, InfiniteData } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { formatDate } from '../members/memberFormat';
import type { MemberResolution, MemberResolutionPage } from './memberResolutionsApi';
import { StateBadge } from './StateBadge';

const TOUCH = 44;

function Item({ resolution: r }: { resolution: MemberResolution }) {
  const { t } = useTranslation();
  return (
    <Card withBorder padding="sm" data-testid="member-resolution" style={r.overdue ? { borderColor: 'var(--mantine-color-red-6)' } : undefined}>
      <Stack gap={6}>
        <Anchor
          component={Link}
          to={`/tasks/${r.taskId}`}
          fw={600}
          style={{ overflowWrap: 'anywhere', minHeight: TOUCH, display: 'inline-flex', alignItems: 'center' }}
        >
          {r.taskTitle || t('memberResolutions.untitledTask')}
        </Anchor>
        <Text size="sm" lineClamp={2} style={{ overflowWrap: 'anywhere' }}>
          {r.instruction}
        </Text>
        <Group gap="xs">
          <StateBadge state={r.state} />
          {r.overdue && (
            <Badge color="red" variant="filled">
              {t('tasks.resolutions.overdue')}
            </Badge>
          )}
          <Text size="sm" c={r.overdue ? 'red' : 'dimmed'} fw={r.overdue ? 600 : undefined}>
            {t('tasks.resolutions.fields.dueDate')}: {formatDate(r.dueDate)}
          </Text>
        </Group>
      </Stack>
    </Card>
  );
}

/** Resolutions of a person (task title link, state badge, due date, overdue mark) with load-more paging. */
export function MemberResolutionList({
  query,
  emptyKey = 'memberResolutions.empty',
}: {
  query: UseInfiniteQueryResult<InfiniteData<MemberResolutionPage>>;
  emptyKey?: string;
}) {
  const { t } = useTranslation();
  if (query.isLoading) return <Loader size="sm" />;
  if (query.isError) return <Alert color="red">{t('memberResolutions.loadFailed')}</Alert>;
  const items = (query.data?.pages ?? []).flatMap((p) => p.items);
  if (items.length === 0) {
    return (
      <Text c="dimmed" size="sm">
        {t(emptyKey)}
      </Text>
    );
  }
  return (
    <Stack gap="xs">
      {items.map((r) => (
        <Item key={r.id} resolution={r} />
      ))}
      {query.hasNextPage && (
        <Button variant="default" mih={TOUCH} loading={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
          {t('tasks.comments.loadMore')}
        </Button>
      )}
    </Stack>
  );
}
