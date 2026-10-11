import { Badge, Card, Group, Stack, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import type { AuditEntry } from './auditApi';
import { diffStates, formatAuditValue } from './auditDiff';
import { formatKyivDateTime } from './kyivTime';

const NIL_UUID = '00000000-0000-0000-0000-000000000000';

/** Short, readable actor reference: "System" for the nil UUID, otherwise the first 8 characters of the id. */
export function ActorLabel({ actorId }: { actorId: string | null }) {
  const { t } = useTranslation();
  if (!actorId || actorId === NIL_UUID) return <>{t('audit.system')}</>;
  return <span title={actorId}>{actorId.slice(0, 8)}</span>;
}

function Value({ value }: { value: unknown }) {
  const { t } = useTranslation();
  const text = formatAuditValue(value);
  return text === null ? (
    <Text span c="dimmed">
      {t('audit.empty')}
    </Text>
  ) : (
    <>{text}</>
  );
}

/** Before/after view of the fields that changed in one audit record. */
export function ChangeList({ entry }: { entry: AuditEntry }) {
  const { t } = useTranslation();
  const changes = diffStates(entry.before, entry.after);
  if (changes.length === 0) {
    return (
      <Text size="sm" c="dimmed">
        {t('audit.noChanges')}
      </Text>
    );
  }
  return (
    <Stack gap="xs" component="ul" m={0} p={0} style={{ listStyle: 'none' }} data-testid="audit-changes">
      {changes.map((c) => (
        <li key={c.field}>
          <Text size="sm" fw={600} style={{ overflowWrap: 'anywhere' }}>
            {c.field}
          </Text>
          <Group gap="xs" wrap="wrap" align="flex-start">
            {!c.added && (
              <Text component="del" size="sm" c="red" aria-label={t('audit.before')} style={{ overflowWrap: 'anywhere' }}>
                <Value value={c.before} />
              </Text>
            )}
            {!c.added && !c.removed && (
              <Text size="sm" c="dimmed" aria-hidden>
                {'->'}
              </Text>
            )}
            {!c.removed && (
              <Text
                component="ins"
                size="sm"
                c="green"
                aria-label={t('audit.after')}
                style={{ overflowWrap: 'anywhere', textDecoration: 'none' }}
              >
                <Value value={c.after} />
              </Text>
            )}
          </Group>
        </li>
      ))}
    </Stack>
  );
}

/** One audit record: action, actor, time (Europe/Kyiv) and the field diff. Shared by the log and EntityHistory. */
export function AuditEntryCard({ entry, showEntity = false }: { entry: AuditEntry; showEntity?: boolean }) {
  const { t, i18n } = useTranslation();
  return (
    <Card withBorder padding="sm" data-testid="audit-entry">
      <Stack gap="xs">
        <Group justify="space-between" wrap="wrap" gap="xs">
          <Badge variant="light">{t(`audit.actions.${entry.action}`, { defaultValue: entry.action })}</Badge>
          <Text size="sm" c="dimmed">
            {formatKyivDateTime(entry.at, i18n.language)}
          </Text>
        </Group>
        <Text size="sm">
          {t('audit.actor')}: <ActorLabel actorId={entry.actorId} />
        </Text>
        {showEntity && (
          <Text size="sm" style={{ overflowWrap: 'anywhere' }}>
            {entry.entityType}
            {entry.entityId ? ` · ${entry.entityId}` : ''}
          </Text>
        )}
        <ChangeList entry={entry} />
      </Stack>
    </Card>
  );
}
