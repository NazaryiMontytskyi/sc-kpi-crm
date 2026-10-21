import { Alert, Box, Button, Drawer, Group, Loader, NavLink, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { articleErrorKey } from './articleLogic';
import { KB_EDIT, type TreeNode } from './knowledgeApi';
import { pathTo } from './treeLogic';
import { useArticleTree } from './useKnowledge';

function TreeItem({ node, activeId, openIds }: { node: TreeNode; activeId?: string; openIds: ReadonlySet<string> }) {
  return (
    <NavLink
      component={Link}
      to={`/knowledge/${node.id}`}
      label={node.title}
      active={node.id === activeId}
      defaultOpened={openIds.has(node.id)}
      childrenOffset={16}
      styles={{ root: { minHeight: 44 }, label: { overflowWrap: 'anywhere' } }}
    >
      {node.children.map((c) => (
        <TreeItem key={c.id} node={c} activeId={activeId} openIds={openIds} />
      ))}
    </NavLink>
  );
}

/** Page tree of the knowledge base; the branch of the open page is expanded. */
export function PageTree({ activeId, onNavigate }: { activeId?: string; onNavigate?: () => void }) {
  const { t } = useTranslation();
  const query = useArticleTree();
  const canEdit = usePermission(KB_EDIT);
  const nodes = query.data ?? [];
  const openIds = new Set(activeId ? pathTo(nodes, activeId) : []);
  return (
    <Stack gap="xs" data-testid="page-tree" onClickCapture={(e) => (e.target as HTMLElement).closest('a') && onNavigate?.()}>
      <Group justify="space-between" gap="xs">
        <Text fw={600}>{t('knowledge.tree.title')}</Text>
        {canEdit && (
          <Button component={Link} to="/knowledge/new" size="compact-sm" variant="light" mih={32}>
            {t('knowledge.tree.newRoot')}
          </Button>
        )}
      </Group>
      {query.isLoading && <Loader size="sm" />}
      {query.isError && <Alert color="red">{t(articleErrorKey(query.error))}</Alert>}
      {query.data && nodes.length === 0 && <Text c="dimmed" size="sm">{t('knowledge.tree.empty')}</Text>}
      <Box>
        {nodes.map((n) => (
          <TreeItem key={n.id} node={n} activeId={activeId} openIds={openIds} />
        ))}
      </Box>
    </Stack>
  );
}

/** Shell of the knowledge pages: tree sidebar on wide screens, a drawer opened from a button on narrow ones. */
export function KnowledgeLayout({ activeId, children }: { activeId?: string; children: ReactNode }) {
  const { t } = useTranslation();
  const [opened, { open, close }] = useDisclosure(false);
  return (
    <Group align="flex-start" wrap="nowrap" gap="lg">
      <Box visibleFrom="md" w={280} style={{ flex: '0 0 280px' }} component="nav" aria-label={t('knowledge.tree.title')}>
        <PageTree activeId={activeId} />
      </Box>
      <Stack gap="md" style={{ flex: '1 1 0', minWidth: 0 }}>
        <Button hiddenFrom="md" variant="default" mih={44} onClick={open} style={{ alignSelf: 'flex-start' }}>
          {t('knowledge.tree.open')}
        </Button>
        <Drawer opened={opened} onClose={close} title={t('knowledge.tree.title')} size="85%">
          <PageTree activeId={activeId} onNavigate={close} />
        </Drawer>
        {children}
      </Stack>
    </Group>
  );
}
