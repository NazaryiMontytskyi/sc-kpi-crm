import { Alert, Anchor, Badge, Button, Card, Group, Loader, Stack, Switch, Table, Text, TextInput, Title } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { MemberAvatar } from './MemberAvatar';
import type { MemberSummary } from './memberApi';
import { formatDate, fullName, matchesSearch, memberFacultyName, telegramLink } from './memberFormat';
import { memberErrorKey } from './memberProblems';
import { MemberFormModal } from './MemberFormModal';
import { TemporaryPasswordModal } from './TemporaryPasswordModal';
import { useMembers } from './useMembers';

const TOUCH = 44;
export const MEMBERS_CREATE = 'members.create';
export const MEMBERS_ARCHIVE = 'members.archive';

function StatusBadge({ member }: { member: MemberSummary }) {
  const { t } = useTranslation();
  if (!member.archived) return null;
  return (
    <Badge color="gray" variant="light">
      {t('members.archivedBadge')}
    </Badge>
  );
}

function TelegramLink({ value }: { value: string }) {
  const href = telegramLink(value);
  if (!href) return <>{value}</>;
  return (
    <Anchor href={href} target="_blank" rel="noopener noreferrer" size="sm">
      @{value.replace(/^@/, '')}
    </Anchor>
  );
}

/** Opaque handle of the one-time password dialog opened after a member is created. */
interface CreatedInfo {
  id: string;
  name: string;
  password: string;
}

/**
 * Member directory: text search (client-side, the backend has no search yet), table on desktop (>= 48em),
 * cards on mobile. Create needs `members.create`; archived members are listed for `members.archive` holders.
 */
export function MembersPage() {
  const { t, i18n } = useTranslation();
  usePageTitle('nav.members');
  const navigate = useNavigate();
  const canCreate = usePermission(MEMBERS_CREATE);
  const canSeeArchived = usePermission(MEMBERS_ARCHIVE);
  const isDesktop = useMediaQuery('(min-width: 48em)');
  const [search, setSearch] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<CreatedInfo | null>(null);
  const query = useMembers(canSeeArchived && showArchived);
  const all = query.data ?? [];
  const members = all.filter((m) => matchesSearch(m, search));
  const loadError = query.isError ? memberErrorKey(query.error) : null;

  return (
    <Stack gap="md">
      <Title order={1} size="h2">
        {t('members.title')}
      </Title>
      <Group justify="space-between" align="flex-end" gap="sm">
        <TextInput
          type="search"
          label={t('members.search')}
          placeholder={t('members.searchPlaceholder')}
          value={search}
          onChange={(e) => setSearch(e.currentTarget.value)}
          style={{ flex: '1 1 16rem', maxWidth: '28rem' }}
          styles={{ input: { minHeight: TOUCH } }}
        />
        {canCreate && (
          <Button mih={TOUCH} onClick={() => setCreating(true)}>
            {t('members.add')}
          </Button>
        )}
      </Group>
      {canSeeArchived && (
        <Switch
          label={t('members.showArchived')}
          checked={showArchived}
          onChange={(e) => setShowArchived(e.currentTarget.checked)}
          styles={{ root: { minHeight: TOUCH, display: 'flex', alignItems: 'center' } }}
        />
      )}

      {query.isLoading && <Loader size="sm" />}
      {loadError && <Alert color="red">{t(loadError === 'problems.UNKNOWN' ? 'members.loadFailed' : loadError)}</Alert>}
      {query.data && all.length === 0 && <Text c="dimmed">{t('members.empty')}</Text>}
      {query.data && all.length > 0 && members.length === 0 && <Text c="dimmed">{t('members.nothingFound')}</Text>}
      {members.length > 0 && (
        <Text size="sm" c="dimmed">
          {t('members.count', { count: members.length })}
        </Text>
      )}

      {members.length > 0 && isDesktop && (
        <Table withTableBorder highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('members.name')}</Table.Th>
              <Table.Th>{t('faculties.select.label')}</Table.Th>
              <Table.Th>{t('members.studyGroup')}</Table.Th>
              <Table.Th>{t('members.telegram')}</Table.Th>
              <Table.Th>{t('members.joinedAt')}</Table.Th>
              <Table.Th>{t('members.status')}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {members.map((m) => (
              <Table.Tr key={m.id}>
                <Table.Td>
                  <Group gap="sm" wrap="nowrap">
                    <MemberAvatar member={m} size={36} />
                    <Anchor component={Link} to={`/members/${m.id}`}>
                      {fullName(m)}
                    </Anchor>
                  </Group>
                </Table.Td>
                <Table.Td>{memberFacultyName(m.faculty, i18n.language)}</Table.Td>
                <Table.Td>{m.studyGroup}</Table.Td>
                <Table.Td>
                  <TelegramLink value={m.telegram} />
                </Table.Td>
                <Table.Td>{formatDate(m.joinedAt)}</Table.Td>
                <Table.Td>
                  <StatusBadge member={m} />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {members.length > 0 && !isDesktop && (
        <Stack gap="sm">
          {members.map((m) => (
            <Card key={m.id} withBorder padding="md" data-testid="member-card">
              <Group wrap="nowrap" align="flex-start" gap="sm">
                <MemberAvatar member={m} size={48} />
                <Stack gap={4} style={{ minWidth: 0, flex: 1 }}>
                  <Group justify="space-between" wrap="nowrap" align="flex-start" gap="xs">
                    <Anchor
                      component={Link}
                      to={`/members/${m.id}`}
                      fw={600}
                      style={{ overflowWrap: 'anywhere', minHeight: TOUCH, display: 'inline-flex', alignItems: 'center' }}
                    >
                      {fullName(m)}
                    </Anchor>
                    <StatusBadge member={m} />
                  </Group>
                  <Text size="sm" c="dimmed" style={{ overflowWrap: 'anywhere' }}>
                    {[memberFacultyName(m.faculty, i18n.language), m.studyGroup].filter(Boolean).join(' · ')}
                  </Text>
                  <TelegramLink value={m.telegram} />
                </Stack>
              </Group>
            </Card>
          ))}
        </Stack>
      )}

      {canCreate && creating && (
        <MemberFormModal
          mode="create"
          member={null}
          onClose={() => setCreating(false)}
          onCreated={(member, password) => {
            setCreating(false);
            setCreated({ id: member.id, name: fullName(member), password });
          }}
        />
      )}
      {created && (
        <TemporaryPasswordModal
          password={created.password}
          memberName={created.name}
          onClose={() => {
            const { id } = created;
            setCreated(null);
            void navigate(`/members/${id}`);
          }}
        />
      )}
    </Stack>
  );
}
