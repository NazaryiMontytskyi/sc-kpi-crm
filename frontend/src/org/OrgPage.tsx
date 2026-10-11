import { ActionIcon, Alert, Anchor, Badge, Box, Button, Card, Group, Loader, Stack, Switch, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import type { OrgNode } from './orgApi';
import { orgErrorKey } from './orgProblems';
import { hierarchy, workingGroups } from './orgTree';
import { ORG_MANAGE, TOUCH, unitTypeKey } from './OrgParts';
import { UnitFormModal } from './UnitModals';
import { useOrgTree } from './useOrg';

function UnitBadges({ node }: { node: OrgNode }) {
  const { t } = useTranslation();
  return (
    <>
      <Badge variant="light" size="sm">
        {t(unitTypeKey(node.type))}
      </Badge>
      {node.archived && (
        <Badge color="gray" variant="light" size="sm">
          {t('org.archivedBadge')}
        </Badge>
      )}
    </>
  );
}

/**
 * One collapsible tree row. The chevron is a 44 px touch target; children are indented by a fixed small step
 * with a guide line so that three levels still fit at 375 px; long names wrap instead of overflowing.
 */
function TreeNode({ node, depth }: { node: OrgNode; depth: number }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(depth < 2);
  const hasChildren = node.children.length > 0;
  return (
    <div role="treeitem" aria-expanded={hasChildren ? open : undefined}>
      <Group gap={4} wrap="nowrap" align="center" data-testid="org-node">
        {hasChildren ? (
          <ActionIcon
            variant="subtle"
            color="gray"
            size={TOUCH}
            aria-label={t(open ? 'org.collapse' : 'org.expand', { name: node.name })}
            onClick={() => setOpen((v) => !v)}
          >
            <span aria-hidden>{open ? '▾' : '▸'}</span>
          </ActionIcon>
        ) : (
          <Box w={TOUCH} h={TOUCH} style={{ flex: '0 0 auto' }} />
        )}
        <Group gap="xs" wrap="wrap" style={{ flex: 1, minWidth: 0 }}>
          <Anchor component={Link} to={`/org/${node.id}`} fw={500} style={{ overflowWrap: 'anywhere' }}>
            {node.name}
          </Anchor>
          <UnitBadges node={node} />
        </Group>
      </Group>
      {hasChildren && open && (
        <>
          <div role="group" style={{ marginLeft: 20, paddingLeft: 8, borderLeft: '1px solid var(--mantine-color-default-border)' }}>
            {node.children.map((c) => (
              <TreeNode key={c.id} node={c} depth={depth + 1} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}

/** Org structure: the collapsible hierarchy, then working groups (outside the hierarchy) as a separate list. */
export function OrgPage() {
  const { t } = useTranslation();
  usePageTitle('nav.org');
  const navigate = useNavigate();
  const canManage = usePermission(ORG_MANAGE);
  const [showArchived, setShowArchived] = useState(false);
  const [creating, setCreating] = useState(false);
  const query = useOrgTree(canManage && showArchived);
  const tree = query.data ?? [];
  const roots = hierarchy(tree);
  const groups = workingGroups(tree);
  const loadError = query.isError ? orgErrorKey(query.error) : null;

  return (
    <Stack gap="md">
      <Group justify="space-between" align="center" gap="sm">
        <Title order={1} size="h2">
          {t('org.title')}
        </Title>
        {canManage && (
          <Button mih={TOUCH} onClick={() => setCreating(true)}>
            {t('org.unit.create')}
          </Button>
        )}
      </Group>
      {canManage && (
        <Switch
          label={t('org.showArchived')}
          checked={showArchived}
          onChange={(e) => setShowArchived(e.currentTarget.checked)}
          styles={{ root: { minHeight: TOUCH, display: 'flex', alignItems: 'center' } }}
        />
      )}

      {query.isLoading && <Loader size="sm" />}
      {loadError && <Alert color="red">{t(loadError === 'problems.UNKNOWN' ? 'org.loadFailed' : loadError)}</Alert>}
      {query.data && tree.length === 0 && <Text c="dimmed">{t('org.empty')}</Text>}

      {roots.length > 0 && (
        <Card withBorder padding="xs" component="section" aria-label={t('org.structure')}>
          <div role="tree">
            {roots.map((n) => (
              <TreeNode key={n.id} node={n} depth={0} />
            ))}
          </div>
        </Card>
      )}

      {groups.length > 0 && (
        <Stack gap="xs" component="section" aria-labelledby="org-groups-title">
          <Title order={2} size="h4" id="org-groups-title">
            {t('org.workingGroups')}
          </Title>
          <Text size="sm" c="dimmed">
            {t('org.workingGroupsHint')}
          </Text>
          <Card withBorder padding="xs">
            <Stack gap={0} data-testid="working-groups">
              {groups.map((g) => (
                <Group key={g.id} gap="xs" wrap="wrap" mih={TOUCH} align="center" px="xs">
                  <Anchor component={Link} to={`/org/${g.id}`} fw={500} style={{ overflowWrap: 'anywhere' }}>
                    {g.name}
                  </Anchor>
                  <UnitBadges node={g} />
                </Group>
              ))}
            </Stack>
          </Card>
        </Stack>
      )}

      {creating && (
        <UnitFormModal
          unit={null}
          tree={tree}
          onClose={() => setCreating(false)}
          onSaved={(created) => navigate(`/org/${created.id}`)}
        />
      )}
    </Stack>
  );
}
