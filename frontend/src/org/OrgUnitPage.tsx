import { Accordion, Alert, Anchor, Badge, Breadcrumbs, Button, Card, Group, Loader, Stack, Switch, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { EntityHistory } from '../audit';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { formatDate } from '../members/memberFormat';
import type { Membership, OrgUnit, Position } from './orgApi';
import { orgErrorKey } from './orgProblems';
import { findNode, pathTo } from './orgTree';
import {
  MemberName,
  ORG_MANAGE,
  POSITIONS_APPOINT,
  POSITIONS_MANAGE,
  TOUCH,
  unitTypeKey,
} from './OrgParts';
import { AppointModal, PositionActionModal, PositionFormModal, type PositionAction } from './PositionModals';
import {
  AddMemberModal,
  MembershipActionModal,
  UnitActionModal,
  UnitFormModal,
  UnitMoveModal,
  type UnitAction,
} from './UnitModals';
import { useOrgTree, useOrgUnit, useUnitMemberships, useUnitPositions } from './useOrg';

/** Entity types under which the org module audits changes. */
const UNIT_AUDIT = 'OrgUnit';

type Dialog =
  | { kind: 'edit' }
  | { kind: 'move' }
  | { kind: 'unitAction'; action: UnitAction }
  | { kind: 'addMember' }
  | { kind: 'membership'; action: 'end' | 'move'; membership: Membership }
  | { kind: 'positionForm'; position: Position | null }
  | { kind: 'positionAction'; action: PositionAction; position: Position }
  | { kind: 'appoint'; position: Position }
  | null;

function PositionCard({
  position,
  canManage,
  canAppoint,
  onAction,
  onEdit,
  onAppoint,
}: {
  position: Position;
  canManage: boolean;
  canAppoint: boolean;
  onAction: (action: PositionAction) => void;
  onEdit: () => void;
  onAppoint: () => void;
}) {
  const { t } = useTranslation();
  const full = position.activeHolderIds.length >= position.capacity;
  return (
    <Card withBorder padding="sm" data-testid="position-card">
      <Stack gap="xs">
        <Group gap="xs" wrap="wrap" align="center">
          <Anchor component={Link} to={`/org/positions/${position.id}`} fw={600} style={{ overflowWrap: 'anywhere' }}>
            {position.title}
          </Anchor>
          {position.isHead && <Badge size="sm">{t('org.position.headBadge')}</Badge>}
          {position.archived && (
            <Badge color="gray" variant="light" size="sm">
              {t('org.archivedBadge')}
            </Badge>
          )}
          {!position.archived && position.vacant && (
            <Badge color="yellow" variant="light" size="sm">
              {t('org.position.vacant')}
            </Badge>
          )}
          <Text size="sm" c="dimmed">
            {t('org.position.places', { used: position.activeHolderIds.length, capacity: position.capacity })}
          </Text>
        </Group>
        {position.activeHolderIds.length > 0 && (
          <Group gap="md" wrap="wrap" data-testid="position-holders">
            {position.activeHolderIds.map((id) => (
              <MemberName key={id} id={id} />
            ))}
          </Group>
        )}
        <Group gap="xs" wrap="wrap">
          {canAppoint && !position.archived && !full && (
            <Button size="sm" mih={TOUCH} onClick={onAppoint}>
              {t('org.appointment.appoint')}
            </Button>
          )}
          {canManage && !position.archived && (
            <>
              <Button size="sm" mih={TOUCH} variant="default" onClick={onEdit}>
                {t('org.position.edit')}
              </Button>
              <Button size="sm" mih={TOUCH} variant="default" onClick={() => onAction('archive')}>
                {t('org.position.archive.open')}
              </Button>
            </>
          )}
          {canManage && position.archived && (
            <Button size="sm" mih={TOUCH} onClick={() => onAction('restore')}>
              {t('org.position.restore.open')}
            </Button>
          )}
        </Group>
      </Stack>
    </Card>
  );
}

function MembershipRow({
  membership,
  canManage,
  onAction,
}: {
  membership: Membership;
  canManage: boolean;
  onAction: (action: 'end' | 'move') => void;
}) {
  const { t } = useTranslation();
  return (
    <Card withBorder padding="sm" data-testid="membership-row">
      <Group justify="space-between" align="center" gap="xs">
        <Stack gap={2} style={{ minWidth: 0, flex: '1 1 12rem' }}>
          <MemberName id={membership.userId} />
          <Text size="sm" c="dimmed">
            {membership.until
              ? t('org.membership.period', { since: formatDate(membership.since), until: formatDate(membership.until) })
              : t('org.membership.sinceDate', { since: formatDate(membership.since) })}
          </Text>
        </Stack>
        {canManage && membership.active && (
          <Group gap="xs" wrap="wrap">
            <Button size="sm" mih={TOUCH} variant="default" onClick={() => onAction('move')}>
              {t('org.membership.move.open')}
            </Button>
            <Button size="sm" mih={TOUCH} variant="default" onClick={() => onAction('end')}>
              {t('org.membership.end.open')}
            </Button>
          </Group>
        )}
      </Group>
    </Card>
  );
}

function UnitDetail({ unit }: { unit: OrgUnit }) {
  const { t } = useTranslation();
  const canManage = usePermission(ORG_MANAGE);
  const canManagePositions = usePermission(POSITIONS_MANAGE);
  const canAppoint = usePermission(POSITIONS_APPOINT);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [showEnded, setShowEnded] = useState(false);
  const [showArchivedPositions, setShowArchivedPositions] = useState(false);
  const treeQuery = useOrgTree(true);
  const tree = treeQuery.data ?? [];
  const node = findNode(tree, unit.id);
  const crumbs = pathTo(tree, unit.id);
  const subUnits = (node?.children ?? []).filter((c) => !c.archived);
  const memberships = useUnitMemberships(unit.id, showEnded);
  const positions = useUnitPositions(unit.id, canManagePositions && showArchivedPositions);
  const activeMemberIds = (memberships.data ?? []).filter((m) => m.active).map((m) => m.userId);
  const positionList = positions.data ?? [];
  const head = positionList.find((p) => p.isHead && !p.archived);
  const unitHasHead = positionList.some((p) => p.isHead && !p.archived);
  const active = !unit.archived;
  const close = () => setDialog(null);

  return (
    <Stack gap="md">
      {crumbs.length > 1 && (
        <Breadcrumbs>
          {crumbs.slice(0, -1).map((c) => (
            <Anchor key={c.id} component={Link} to={`/org/${c.id}`} size="sm">
              {c.name}
            </Anchor>
          ))}
        </Breadcrumbs>
      )}
      <Anchor component={Link} to="/org" style={{ minHeight: TOUCH, display: 'inline-flex', alignItems: 'center' }}>
        {t('org.backToStructure')}
      </Anchor>

      <Stack gap={4}>
        <Title order={1} size="h2" style={{ overflowWrap: 'anywhere' }}>
          {unit.name}
        </Title>
        <Group gap="xs">
          <Badge variant="light">{t(unitTypeKey(unit.type))}</Badge>
          {unit.archived && (
            <Badge color="gray" variant="light">
              {t('org.archivedBadge')}
            </Badge>
          )}
        </Group>
      </Stack>
      {unit.archived && <Alert color="yellow">{t('org.unit.archivedNotice')}</Alert>}

      {canManage && (
        <Group gap="xs" wrap="wrap">
          {active && (
            <>
              <Button mih={TOUCH} onClick={() => setDialog({ kind: 'edit' })}>
                {t('org.unit.edit')}
              </Button>
              <Button mih={TOUCH} variant="default" onClick={() => setDialog({ kind: 'move' })}>
                {t('org.unit.move')}
              </Button>
              <Button mih={TOUCH} variant="default" onClick={() => setDialog({ kind: 'unitAction', action: 'archive' })}>
                {t('org.unit.archive.open')}
              </Button>
            </>
          )}
          {unit.archived && (
            <Button mih={TOUCH} onClick={() => setDialog({ kind: 'unitAction', action: 'restore' })}>
              {t('org.unit.restore.open')}
            </Button>
          )}
        </Group>
      )}

      <Card withBorder padding="md">
        <Stack gap="sm">
          <div>
            <Text size="sm" c="dimmed">
              {t('org.unit.description')}
            </Text>
            {unit.description ? (
              <Text style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{unit.description}</Text>
            ) : (
              <Text c="dimmed">{t('org.unit.noDescription')}</Text>
            )}
          </div>
          <div data-testid="unit-head">
            <Text size="sm" c="dimmed">
              {t('org.unit.head')}
            </Text>
            {head && head.activeHolderIds.length > 0 ? (
              <MemberName id={head.activeHolderIds[0]} />
            ) : (
              <Text c="dimmed">{t('org.unit.noHead')}</Text>
            )}
          </div>
          {subUnits.length > 0 && (
            <div>
              <Text size="sm" c="dimmed">
                {t('org.unit.subUnits')}
              </Text>
              <Group gap="md" wrap="wrap">
                {subUnits.map((c) => (
                  <Anchor key={c.id} component={Link} to={`/org/${c.id}`} style={{ overflowWrap: 'anywhere' }}>
                    {c.name}
                  </Anchor>
                ))}
              </Group>
            </div>
          )}
        </Stack>
      </Card>

      <Stack gap="xs" component="section" aria-labelledby="unit-positions">
        <Group justify="space-between" align="center" gap="xs">
          <Title order={2} size="h4" id="unit-positions">
            {t('org.positions')}
          </Title>
          {canManagePositions && active && (
            <Button size="sm" mih={TOUCH} onClick={() => setDialog({ kind: 'positionForm', position: null })}>
              {t('org.position.create')}
            </Button>
          )}
        </Group>
        {canManagePositions && (
          <Switch
            label={t('org.position.showArchived')}
            checked={showArchivedPositions}
            onChange={(e) => setShowArchivedPositions(e.currentTarget.checked)}
            styles={{ root: { minHeight: TOUCH, display: 'flex', alignItems: 'center' } }}
          />
        )}
        {positions.isLoading && <Loader size="sm" />}
        {positions.isError && <Alert color="red">{t(orgErrorKey(positions.error))}</Alert>}
        {positions.isSuccess && positionList.length === 0 && <Text c="dimmed">{t('org.position.empty')}</Text>}
        {positionList.map((p) => (
          <PositionCard
            key={p.id}
            position={p}
            canManage={canManagePositions}
            canAppoint={canAppoint && active}
            onEdit={() => setDialog({ kind: 'positionForm', position: p })}
            onAction={(action) => setDialog({ kind: 'positionAction', action, position: p })}
            onAppoint={() => setDialog({ kind: 'appoint', position: p })}
          />
        ))}
      </Stack>

      <Stack gap="xs" component="section" aria-labelledby="unit-members">
        <Group justify="space-between" align="center" gap="xs">
          <Title order={2} size="h4" id="unit-members">
            {t('org.members')}
          </Title>
          {canManage && active && (
            <Button size="sm" mih={TOUCH} onClick={() => setDialog({ kind: 'addMember' })}>
              {t('org.membership.add')}
            </Button>
          )}
        </Group>
        <Switch
          label={t('org.membership.showEnded')}
          checked={showEnded}
          onChange={(e) => setShowEnded(e.currentTarget.checked)}
          styles={{ root: { minHeight: TOUCH, display: 'flex', alignItems: 'center' } }}
        />
        {memberships.isLoading && <Loader size="sm" />}
        {memberships.isError && <Alert color="red">{t(orgErrorKey(memberships.error))}</Alert>}
        {memberships.isSuccess && memberships.data.length === 0 && <Text c="dimmed">{t('org.membership.empty')}</Text>}
        {(memberships.data ?? []).map((m) => (
          <MembershipRow
            key={m.id}
            membership={m}
            canManage={canManage}
            onAction={(action) => setDialog({ kind: 'membership', action, membership: m })}
          />
        ))}
      </Stack>

      <Accordion variant="contained">
        <Accordion.Item value="history">
          <Accordion.Control style={{ minHeight: TOUCH }}>{t('org.history')}</Accordion.Control>
          <Accordion.Panel>
            <EntityHistory entityType={UNIT_AUDIT} entityId={unit.id} />
          </Accordion.Panel>
        </Accordion.Item>
      </Accordion>

      {dialog?.kind === 'edit' && <UnitFormModal unit={unit} tree={tree} onClose={close} />}
      {dialog?.kind === 'move' && <UnitMoveModal unit={unit} tree={tree} onClose={close} />}
      {dialog?.kind === 'unitAction' && <UnitActionModal action={dialog.action} unit={unit} onClose={close} />}
      {dialog?.kind === 'addMember' && <AddMemberModal unit={unit} memberIds={activeMemberIds} onClose={close} />}
      {dialog?.kind === 'membership' && (
        <MembershipActionModal action={dialog.action} membership={dialog.membership} tree={tree} onClose={close} />
      )}
      {dialog?.kind === 'positionForm' && (
        <PositionFormModal position={dialog.position} unitId={unit.id} unitHasHead={unitHasHead} onClose={close} />
      )}
      {dialog?.kind === 'positionAction' && (
        <PositionActionModal action={dialog.action} position={dialog.position} onClose={close} />
      )}
      {dialog?.kind === 'appoint' && <AppointModal position={dialog.position} onClose={close} />}
    </Stack>
  );
}

/** Unit page: description, derived head, positions with holders, members; manage actions are permission-gated. */
export function OrgUnitPage() {
  const { t } = useTranslation();
  usePageTitle('nav.org');
  const { id = '' } = useParams();
  const query = useOrgUnit(id);

  if (query.isLoading) return <Loader size="sm" />;
  if (query.isError || !query.data) {
    return (
      <Stack gap="md">
        <Anchor component={Link} to="/org">
          {t('org.backToStructure')}
        </Anchor>
        <Alert color="red">{t(query.isError ? orgErrorKey(query.error) : 'problems.NOT_FOUND')}</Alert>
      </Stack>
    );
  }
  return <UnitDetail unit={query.data} />;
}
