import { ActionIcon, Alert, Box, Button, Group, Loader, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { MemberSummary } from '../members/memberApi';
import { formatDate, fullName } from '../members/memberFormat';
import { flattenChain } from './resolutionLogic';
import { StateBadge } from './StateBadge';
import { useResolutionChain } from './useResolutions';

const TOUCH = 44;
/** Indent per level; with the 3-level cap the tree needs at most 54px, which fits a 375 px screen. */
const INDENT_PX = 18;

/**
 * Collapsible redirect-chain tree of one resolution. Loaded on first expand; each level is
 * indented (capped) and joined with a connector line, and nodes with redirects can be folded.
 */
export function ResolutionChain({ resolutionId, members }: { resolutionId: string; members: readonly MemberSummary[] }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [collapsed, setCollapsed] = useState<ReadonlySet<string>>(new Set());
  const chain = useResolutionChain(resolutionId, open);

  const toggleNode = (id: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  const rows = chain.data ? flattenChain(chain.data, collapsed) : [];
  const nameOf = (id: string) => {
    const m = members.find((x) => x.id === id);
    return m ? fullName(m) : t('tasks.comments.unknownAuthor');
  };

  return (
    <Stack gap="xs" data-testid="resolution-chain">
      <Group>
        <Button mih={TOUCH} variant="subtle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
          {t(open ? 'tasks.resolutions.chain.hide' : 'tasks.resolutions.chain.show')}
        </Button>
      </Group>
      {open && chain.isLoading && <Loader size="sm" />}
      {open && chain.isError && <Alert color="red">{t('tasks.resolutions.chain.loadFailed')}</Alert>}
      {open && chain.data && (
        <Stack gap={4} role="tree" aria-label={t('tasks.resolutions.chain.title')}>
          {rows.map(({ node, indent, hasChildren }) => {
            const folded = collapsed.has(node.id);
            return (
              <Group
                key={node.id}
                role="treeitem"
                aria-level={indent + 1}
                aria-expanded={hasChildren ? !folded : undefined}
                aria-current={node.id === resolutionId ? 'true' : undefined}
                data-testid="chain-node"
                data-indent={indent}
                wrap="nowrap"
                align="flex-start"
                gap={4}
                ml={indent * INDENT_PX}
                pl={indent > 0 ? 'xs' : 0}
                style={{
                  borderLeft: indent > 0 ? '2px solid var(--mantine-color-default-border)' : undefined,
                  minWidth: 0,
                }}
              >
                {hasChildren ? (
                  <ActionIcon
                    variant="subtle"
                    color="gray"
                    size={TOUCH - 8}
                    aria-label={t(folded ? 'tasks.resolutions.chain.expandNode' : 'tasks.resolutions.chain.collapseNode')}
                    onClick={() => toggleNode(node.id)}
                  >
                    {folded ? '▸' : '▾'}
                  </ActionIcon>
                ) : (
                  <Box w={TOUCH - 8} style={{ flexShrink: 0 }} />
                )}
                <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
                  <Group gap="xs">
                    <StateBadge state={node.state} />
                    {node.id === resolutionId && (
                      <Text size="xs" c="dimmed">
                        {t('tasks.resolutions.chain.current')}
                      </Text>
                    )}
                  </Group>
                  <Text size="sm" style={{ overflowWrap: 'anywhere' }}>
                    {nameOf(node.executorId)} · {formatDate(node.dueDate)}
                  </Text>
                  <Text size="xs" c="dimmed" lineClamp={2} style={{ overflowWrap: 'anywhere' }}>
                    {node.instruction}
                  </Text>
                </Stack>
              </Group>
            );
          })}
        </Stack>
      )}
    </Stack>
  );
}
