import { Accordion, Alert, Anchor, Badge, Button, Card, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { EntityHistory } from '../audit';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { formatDate } from '../members/memberFormat';
import { useRoles } from '../roles/useRoles';
import type { Appointment, Position } from './orgApi';
import { orgErrorKey } from './orgProblems';
import { findNode } from './orgTree';
import { MemberName, POSITIONS_APPOINT, POSITIONS_MANAGE, TOUCH } from './OrgParts';
import {
  AppointModal,
  EndAppointmentModal,
  PositionActionModal,
  PositionFormModal,
  type PositionAction,
} from './PositionModals';
import { useOrgTree, usePosition, usePositionHistory, useUnitPositions } from './useOrg';

const POSITION_AUDIT = 'Position';

type Dialog =
  | { kind: 'edit' }
  | { kind: 'action'; action: PositionAction }
  | { kind: 'appoint' }
  | { kind: 'end'; appointment: Appointment }
  | null;

function HistoryRow({
  appointment,
  canEnd,
  onEnd,
}: {
  appointment: Appointment;
  canEnd: boolean;
  onEnd: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Card withBorder padding="sm" data-testid="appointment-row">
      <Group justify="space-between" align="center" gap="xs">
        <Stack gap={2} style={{ minWidth: 0, flex: '1 1 12rem' }}>
          <Group gap="xs" wrap="wrap">
            <MemberName id={appointment.userId} />
            {appointment.active && (
              <Badge size="sm" color="green" variant="light">
                {t('org.appointment.current')}
              </Badge>
            )}
          </Group>
          <Text size="sm" c="dimmed">
            {appointment.endDate
              ? t('org.membership.period', {
                  since: formatDate(appointment.startDate),
                  until: formatDate(appointment.endDate),
                })
              : t('org.membership.sinceDate', { since: formatDate(appointment.startDate) })}
          </Text>
        </Stack>
        {canEnd && appointment.active && (
          <Button size="sm" mih={TOUCH} variant="default" onClick={onEnd}>
            {t('org.appointment.end')}
          </Button>
        )}
      </Group>
    </Card>
  );
}

function PositionDetail({ position }: { position: Position }) {
  const { t } = useTranslation();
  const canManage = usePermission(POSITIONS_MANAGE);
  const canAppoint = usePermission(POSITIONS_APPOINT);
  const [dialog, setDialog] = useState<Dialog>(null);
  const history = usePositionHistory(position.id);
  const roles = useRoles(true);
  const tree = useOrgTree(true);
  const siblings = useUnitPositions(position.orgUnitId, true);
  const unit = tree.data ? findNode(tree.data, position.orgUnitId) : null;
  const boundRoles = position.defaultRoleIds.map((id) => roles.data?.find((r) => r.id === id)?.name ?? '…');
  const unitHasHead = (siblings.data ?? []).some((p) => p.isHead && !p.archived);
  const full = position.activeHolderIds.length >= position.capacity;
  const close = () => setDialog(null);

  return (
    <Stack gap="md">
      <Anchor
        component={Link}
        to={`/org/${position.orgUnitId}`}
        style={{ minHeight: TOUCH, display: 'inline-flex', alignItems: 'center' }}
      >
        {t('org.position.backToUnit', { name: unit?.name ?? t('org.unit.thisUnit') })}
      </Anchor>

      <Stack gap={4}>
        <Title order={1} size="h2" style={{ overflowWrap: 'anywhere' }}>
          {position.title}
        </Title>
        <Group gap="xs">
          {position.isHead && <Badge>{t('org.position.headBadge')}</Badge>}
          {position.archived && (
            <Badge color="gray" variant="light">
              {t('org.archivedBadge')}
            </Badge>
          )}
          {!position.archived && position.vacant && (
            <Badge color="yellow" variant="light">
              {t('org.position.vacant')}
            </Badge>
          )}
        </Group>
      </Stack>
      {position.archived && <Alert color="yellow">{t('org.position.archivedNotice')}</Alert>}

      {(canManage || (canAppoint && !position.archived && !full)) && (
        <Group gap="xs" wrap="wrap">
          {canAppoint && !position.archived && !full && (
            <Button mih={TOUCH} onClick={() => setDialog({ kind: 'appoint' })}>
              {t('org.appointment.appoint')}
            </Button>
          )}
          {canManage && !position.archived && (
            <>
              <Button mih={TOUCH} variant="default" onClick={() => setDialog({ kind: 'edit' })}>
                {t('org.position.edit')}
              </Button>
              <Button mih={TOUCH} variant="default" onClick={() => setDialog({ kind: 'action', action: 'archive' })}>
                {t('org.position.archive.open')}
              </Button>
            </>
          )}
          {canManage && position.archived && (
            <Button mih={TOUCH} onClick={() => setDialog({ kind: 'action', action: 'restore' })}>
              {t('org.position.restore.open')}
            </Button>
          )}
        </Group>
      )}

      <Card withBorder padding="md">
        <Stack gap="sm">
          <div>
            <Text size="sm" c="dimmed">
              {t('org.position.capacity')}
            </Text>
            <Text>{t('org.position.places', { used: position.activeHolderIds.length, capacity: position.capacity })}</Text>
          </div>
          <div data-testid="position-roles">
            <Text size="sm" c="dimmed">
              {t('org.position.roles')}
            </Text>
            {boundRoles.length > 0 ? (
              <Group gap="xs" wrap="wrap">
                {boundRoles.map((name, i) => (
                  <Badge key={`${name}-${i}`} variant="light">
                    {name}
                  </Badge>
                ))}
              </Group>
            ) : (
              <Text c="dimmed">{t('org.position.noRoles')}</Text>
            )}
          </div>
        </Stack>
      </Card>

      <Stack gap="xs" component="section" aria-labelledby="position-history">
        <Title order={2} size="h4" id="position-history">
          {t('org.position.historyTitle')}
        </Title>
        <Text size="sm" c="dimmed">
          {t('org.position.historyHint')}
        </Text>
        {history.isLoading && <Loader size="sm" />}
        {history.isError && <Alert color="red">{t(orgErrorKey(history.error))}</Alert>}
        {history.isSuccess && history.data.length === 0 && <Text c="dimmed">{t('org.position.historyEmpty')}</Text>}
        {(history.data ?? []).map((a) => (
          <HistoryRow
            key={a.id}
            appointment={a}
            canEnd={canAppoint}
            onEnd={() => setDialog({ kind: 'end', appointment: a })}
          />
        ))}
      </Stack>

      <Accordion variant="contained">
        <Accordion.Item value="changes">
          <Accordion.Control style={{ minHeight: TOUCH }}>{t('org.history')}</Accordion.Control>
          <Accordion.Panel>
            <EntityHistory entityType={POSITION_AUDIT} entityId={position.id} />
          </Accordion.Panel>
        </Accordion.Item>
      </Accordion>

      {dialog?.kind === 'edit' && (
        <PositionFormModal position={position} unitId={position.orgUnitId} unitHasHead={unitHasHead} onClose={close} />
      )}
      {dialog?.kind === 'action' && <PositionActionModal action={dialog.action} position={position} onClose={close} />}
      {dialog?.kind === 'appoint' && <AppointModal position={position} onClose={close} />}
      {dialog?.kind === 'end' && <EndAppointmentModal appointment={dialog.appointment} onClose={close} />}
    </Stack>
  );
}

/** Position page: bound roles, capacity, holders and the full appointment history (who held it and when). */
export function PositionPage() {
  const { t } = useTranslation();
  usePageTitle('nav.org');
  const { id = '' } = useParams();
  const query = usePosition(id);

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
  return <PositionDetail position={query.data} />;
}
