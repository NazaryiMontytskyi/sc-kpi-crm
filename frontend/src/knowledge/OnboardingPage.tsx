import { Alert, Anchor, Badge, Button, Card, Checkbox, Group, Loader, Progress, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { KnowledgeLayout } from './KnowledgeLayout';
import { ONBOARDING_MANAGE, type OnboardingList } from './onboardingApi';
import { onboardingErrorKey, readProgress } from './onboardingLogic';
import { useMyOnboarding, useSetReadMark } from './useOnboarding';

function ListCard({ list }: { list: OnboardingList }) {
  const { t } = useTranslation();
  const setRead = useSetReadMark();
  const { read, total } = readProgress(list.items);
  return (
    <Card withBorder padding="md" component="section" data-testid="onboarding-list" aria-label={list.title}>
      <Stack gap="sm">
        <Group justify="space-between" gap="xs" wrap="wrap">
          <Title order={2} size="h4" style={{ overflowWrap: 'anywhere' }}>
            {list.title}
          </Title>
          <Badge variant="light">{list.orgUnitName ?? t('knowledge.onboarding.general')}</Badge>
        </Group>
        {total > 0 && (
          <Stack gap={4}>
            <Text size="sm" c="dimmed">
              {t('knowledge.onboarding.progress', { read, total })}
            </Text>
            <Progress value={(read / total) * 100} aria-label={t('knowledge.onboarding.progress', { read, total })} />
          </Stack>
        )}
        {total === 0 && <Text c="dimmed">{t('knowledge.onboarding.listEmpty')}</Text>}
        <Stack gap="xs" component="ol" m={0} pl={0} style={{ listStyle: 'none' }}>
          {list.items.map((item, index) => (
            <Group key={item.articleId} component="li" wrap="nowrap" gap="sm" align="center" mih={44}>
              <Checkbox
                size="md"
                checked={item.read}
                aria-label={t('knowledge.onboarding.markRead', { title: item.title })}
                disabled={setRead.isPending}
                onChange={(e) => setRead.mutate({ articleId: item.articleId, read: e.currentTarget.checked })}
              />
              <Anchor component={Link} to={`/knowledge/${item.articleId}`} style={{ overflowWrap: 'anywhere', minWidth: 0 }}>
                {index + 1}. {item.title}
              </Anchor>
              {item.restricted && (
                <Badge variant="light" color="orange" size="sm">
                  {t('knowledge.restrictedBadge')}
                </Badge>
              )}
            </Group>
          ))}
        </Stack>
        {setRead.isError && <Alert color="red">{t(onboardingErrorKey(setRead.error))}</Alert>}
      </Stack>
    </Card>
  );
}

/** "For newcomers": the general reading list plus the lists of the caller own departments, with private read marks. */
export function OnboardingPage() {
  const { t } = useTranslation();
  usePageTitle('knowledge.onboarding.title');
  const canManage = usePermission(ONBOARDING_MANAGE);
  const query = useMyOnboarding();
  return (
    <KnowledgeLayout>
      <Group justify="space-between" align="center" gap="sm">
        <Title order={1} size="h2">
          {t('knowledge.onboarding.title')}
        </Title>
        {canManage && (
          <Button component={Link} to="/knowledge/onboarding/manage" variant="default" mih={44}>
            {t('knowledge.onboarding.manage')}
          </Button>
        )}
      </Group>
      <Text size="sm" c="dimmed">
        {t('knowledge.onboarding.privacy')}
      </Text>
      {query.isLoading && <Loader size="sm" />}
      {query.isError && <Alert color="red">{t(onboardingErrorKey(query.error))}</Alert>}
      {query.data && query.data.length === 0 && <Text c="dimmed">{t('knowledge.onboarding.empty')}</Text>}
      <Stack gap="md">
        {(query.data ?? []).map((l) => (
          <ListCard key={l.id} list={l} />
        ))}
      </Stack>
    </KnowledgeLayout>
  );
}
