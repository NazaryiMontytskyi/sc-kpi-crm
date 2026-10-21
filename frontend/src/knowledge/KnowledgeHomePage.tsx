import { Alert, Anchor, Badge, Button, Card, Group, Loader, Stack, Switch, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { formatKyivDateTime } from '../audit/kyivTime';
import { usePageTitle } from '../layout/pageTitle';
import { articleErrorKey } from './articleLogic';
import { KB_ARCHIVE, KB_EDIT } from './knowledgeApi';
import { KnowledgeLayout } from './KnowledgeLayout';
import { useArticles, useTags } from './useKnowledge';

/** Knowledge base start page: tags and the list of pages (all, or one tag). */
export function KnowledgeHomePage() {
  const { t, i18n } = useTranslation();
  usePageTitle('nav.knowledge');
  const [params, setParams] = useSearchParams();
  const tag = params.get('tag') ?? undefined;
  const showArchived = params.get('archived') === '1';
  const canEdit = usePermission(KB_EDIT);
  const canArchive = usePermission(KB_ARCHIVE);
  const tags = useTags();
  const articles = useArticles(tag, canArchive && showArchived);

  const update = (next: { tag?: string; archived?: boolean }) => {
    const p = new URLSearchParams(params);
    if ('tag' in next) {
      if (next.tag) p.set('tag', next.tag);
      else p.delete('tag');
    }
    if ('archived' in next) {
      if (next.archived) p.set('archived', '1');
      else p.delete('archived');
    }
    setParams(p, { replace: true });
  };

  return (
    <KnowledgeLayout>
      <Group justify="space-between" align="center" gap="sm">
        <Title order={1} size="h2">
          {t('knowledge.title')}
        </Title>
        {canEdit && (
          <Button component={Link} to="/knowledge/new" mih={44}>
            {t('knowledge.newPage')}
          </Button>
        )}
      </Group>

      {tags.data && tags.data.length > 0 && (
        <Group gap="xs" aria-label={t('knowledge.tags')} data-testid="tag-filter">
          <Badge
            component="button"
            type="button"
            variant={tag ? 'outline' : 'filled'}
            size="lg"
            style={{ cursor: 'pointer' }}
            onClick={() => update({ tag: undefined })}
          >
            {t('knowledge.allTags')}
          </Badge>
          {tags.data.map((x) => (
            <Badge
              key={x.tag}
              component="button"
              type="button"
              size="lg"
              variant={tag === x.tag ? 'filled' : 'outline'}
              style={{ cursor: 'pointer' }}
              onClick={() => update({ tag: x.tag })}
            >
              {x.tag} · {x.count}
            </Badge>
          ))}
        </Group>
      )}
      {canArchive && (
        <Switch
          label={t('knowledge.showArchived')}
          checked={showArchived}
          onChange={(e) => update({ archived: e.currentTarget.checked })}
          styles={{ root: { minHeight: 44, display: 'flex', alignItems: 'center' } }}
        />
      )}

      {articles.isLoading && <Loader size="sm" />}
      {articles.isError && <Alert color="red">{t(articleErrorKey(articles.error))}</Alert>}
      {articles.data && articles.data.length === 0 && <Text c="dimmed">{t('knowledge.empty')}</Text>}
      <Stack gap="xs" data-testid="article-list">
        {(articles.data ?? []).map((a) => (
          <Card key={a.id} withBorder padding="sm" component="article">
            <Group justify="space-between" wrap="wrap" gap="xs">
              <Group gap="xs" style={{ minWidth: 0 }}>
                <Anchor component={Link} to={`/knowledge/${a.id}`} fw={600} style={{ overflowWrap: 'anywhere' }}>
                  {a.title}
                </Anchor>
                {a.restricted && (
                  <Badge variant="light" color="orange" size="sm">
                    {t('knowledge.restrictedBadge')}
                  </Badge>
                )}
                {a.archived && (
                  <Badge variant="outline" color="gray" size="sm">
                    {t('knowledge.archivedBadge')}
                  </Badge>
                )}
              </Group>
              <Text size="xs" c="dimmed">
                {formatKyivDateTime(a.updatedAt, i18n.language)}
              </Text>
            </Group>
            {a.tags.length > 0 && (
              <Group gap={4} mt={4}>
                {a.tags.map((x) => (
                  <Badge key={x} size="sm" variant="light">
                    {x}
                  </Badge>
                ))}
              </Group>
            )}
          </Card>
        ))}
      </Stack>
    </KnowledgeLayout>
  );
}
