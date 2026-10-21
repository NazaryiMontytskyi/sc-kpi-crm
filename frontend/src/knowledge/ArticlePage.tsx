import { Alert, Anchor, Badge, Breadcrumbs, Button, Group, Loader, Modal, Select, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { formatKyivDateTime } from '../audit/kyivTime';
import { ApiProblemError } from '../api/problem';
import { usePageTitle } from '../layout/pageTitle';
import { articleErrorKey } from './articleLogic';
import { ArticleView } from './editor/ArticleView';
import { KB_ARCHIVE, KB_EDIT } from './knowledgeApi';
import { KnowledgeLayout } from './KnowledgeLayout';
import { VersionHistoryDrawer } from './VersionHistoryDrawer';
import { parentOptions } from './treeLogic';
import { useArticle, useArticleMutations, useArticleTree } from './useKnowledge';

const ROOT_VALUE = '__root__';

function MoveModal({ id, parentId, onClose }: { id: string; parentId: string | null; onClose: () => void }) {
  const { t } = useTranslation();
  const tree = useArticleTree();
  const { move } = useArticleMutations();
  const [value, setValue] = useState<string>(parentId ?? ROOT_VALUE);
  const options = [{ value: ROOT_VALUE, label: t('knowledge.move.root') }, ...parentOptions(tree.data ?? [], id)];
  return (
    <Modal opened onClose={onClose} title={t('knowledge.move.title')}>
      <Stack>
        <Select
          label={t('knowledge.move.parent')}
          data={options}
          value={value}
          onChange={(v) => v && setValue(v)}
          allowDeselect={false}
          comboboxProps={{ withinPortal: true }}
        />
        {move.isError && <Alert color="red">{t(articleErrorKey(move.error))}</Alert>}
        <Group justify="flex-end">
          <Button variant="default" mih={44} onClick={onClose}>
            {t('knowledge.cancel')}
          </Button>
          <Button
            mih={44}
            loading={move.isPending}
            onClick={() => move.mutate({ id, parentId: value === ROOT_VALUE ? null : value }, { onSuccess: onClose })}
          >
            {t('knowledge.move.apply')}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

/** Read mode of one article: breadcrumbs, tags, content and the actions the caller's permissions allow. */
export function ArticlePage() {
  const { t, i18n } = useTranslation();
  usePageTitle('nav.knowledge');
  const { id } = useParams();
  const navigate = useNavigate();
  const canEdit = usePermission(KB_EDIT);
  const canArchive = usePermission(KB_ARCHIVE);
  const query = useArticle(id);
  const { archive, restore } = useArticleMutations();
  const [moving, setMoving] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const article = query.data;
  const actionError = archive.error ?? restore.error;

  const notFound = query.error instanceof ApiProblemError && query.error.status === 404;

  return (
    <KnowledgeLayout activeId={id}>
      {query.isLoading && <Loader size="sm" />}
      {query.isError && <Alert color="red">{t(notFound ? 'knowledge.notFound' : articleErrorKey(query.error))}</Alert>}
      {article && (
        <Stack gap="md">
          <Breadcrumbs separatorMargin={6} aria-label={t('knowledge.breadcrumbs')}>
            <Anchor component={Link} to="/knowledge" size="sm">
              {t('nav.knowledge')}
            </Anchor>
            {article.ancestors.map((a) => (
              <Anchor key={a.id} component={Link} to={`/knowledge/${a.id}`} size="sm">
                {a.title}
              </Anchor>
            ))}
            <Text size="sm" aria-current="page">
              {article.title}
            </Text>
          </Breadcrumbs>

          <Group justify="space-between" align="flex-start" gap="sm">
            <Title order={1} size="h2" style={{ overflowWrap: 'anywhere', minWidth: 0 }}>
              {article.title}
            </Title>
            <Group gap="xs">
              {canEdit && !article.archived && (
                <Button component={Link} to={`/knowledge/${article.id}/edit`} mih={44}>
                  {t('knowledge.edit')}
                </Button>
              )}
              {canEdit && !article.archived && (
                <Button component={Link} to={`/knowledge/new?parent=${article.id}`} variant="light" mih={44}>
                  {t('knowledge.newSubpage')}
                </Button>
              )}
              <Button variant="default" mih={44} onClick={() => setHistoryOpen(true)}>
                {t('knowledge.history')}
              </Button>
              {canEdit && !article.archived && (
                <Button variant="default" mih={44} onClick={() => setMoving(true)}>
                  {t('knowledge.move.action')}
                </Button>
              )}
              {canArchive && !article.archived && (
                <Button
                  variant="default"
                  color="red"
                  mih={44}
                  loading={archive.isPending}
                  onClick={() => archive.mutate(article.id, { onSuccess: () => navigate('/knowledge') })}
                >
                  {t('knowledge.archive')}
                </Button>
              )}
              {canArchive && article.archived && (
                <Button variant="default" mih={44} loading={restore.isPending} onClick={() => restore.mutate(article.id)}>
                  {t('knowledge.restore')}
                </Button>
              )}
            </Group>
          </Group>

          {actionError && <Alert color="red">{t(articleErrorKey(actionError))}</Alert>}
          {article.archived && <Alert color="yellow">{t('knowledge.archivedNotice')}</Alert>}

          <Group gap="xs">
            {article.restricted && (
              <Badge variant="light" color="orange">
                {t('knowledge.restrictedBadge')}
              </Badge>
            )}
            {article.tags.map((tag) => (
              <Badge key={tag} variant="light" component={Link} to={`/knowledge?tag=${encodeURIComponent(tag)}`} style={{ cursor: 'pointer' }}>
                {tag}
              </Badge>
            ))}
            <Text size="xs" c="dimmed">
              {t('knowledge.updatedAt', { date: formatKyivDateTime(article.updatedAt, i18n.language) })}
            </Text>
          </Group>

          <ArticleView content={article.content} />
          {historyOpen && (
            <VersionHistoryDrawer
              articleId={article.id}
              currentVersion={article.version}
              canRestore={canEdit && !article.archived}
              onClose={() => setHistoryOpen(false)}
            />
          )}
          {moving && <MoveModal id={article.id} parentId={article.parentArticleId} onClose={() => setMoving(false)} />}
        </Stack>
      )}
    </KnowledgeLayout>
  );
}
