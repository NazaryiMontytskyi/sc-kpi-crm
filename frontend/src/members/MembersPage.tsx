import { Alert, Anchor, Badge, Button, Card, Drawer, Group, Loader, Pagination, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useDebouncedValue, useMediaQuery } from '@mantine/hooks';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { MemberAvatar } from './MemberAvatar';
import type { MemberSummary } from './memberApi';
import { formatDate, fullName, memberFacultyName, telegramLink } from './memberFormat';
import {
  EMPTY_FILTERS,
  activeFilterCount,
  parseFilters,
  toSearchParams,
  withFilter,
  type MemberFilters,
} from './memberFilters';
import { MemberFiltersPanel } from './MemberFiltersPanel';
import { memberErrorKey } from './memberProblems';
import { MemberFormModal } from './MemberFormModal';
import { TemporaryPasswordModal } from './TemporaryPasswordModal';
import { useMemberDirectory } from './useMembers';

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
 * Member directory (INC-021): server-side search, filters and pagination; the state lives in the URL query string
 * so a view can be shared. Filters are an inline panel on desktop (>= 48em) and a drawer on mobile; the list is a
 * table on desktop and cards on mobile. Create needs `members.create`; archived members are offered to
 * `members.archive` holders only (the backend enforces it).
 */
export function MembersPage() {
  const { t, i18n } = useTranslation();
  usePageTitle('nav.members');
  const navigate = useNavigate();
  const canCreate = usePermission(MEMBERS_CREATE);
  const canSeeArchived = usePermission(MEMBERS_ARCHIVE);
  const isDesktop = useMediaQuery('(min-width: 48em)');
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => parseFilters(params), [params]);
  // The input shows what the user is typing (draft) or, when there is no draft, the URL value.
  const [draft, setDraft] = useState<string | null>(null);
  const [seenQ, setSeenQ] = useState(filters.q);
  if (seenQ !== filters.q) {
    // URL -> text (shared link opened, reset pressed, history navigation): drop a draft that disagrees.
    setSeenQ(filters.q);
    if (draft !== null && draft.trim() !== filters.q.trim()) setDraft(null);
  }
  const searchText = draft ?? filters.q;
  const [debouncedText] = useDebouncedValue(searchText, 300);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<CreatedInfo | null>(null);
  // Non-holders never see archived members: drop a status that was typed into the URL by hand.
  const effective: MemberFilters = canSeeArchived ? filters : { ...filters, status: 'ACTIVE' };
  const query = useMemberDirectory(effective);
  const members = query.data?.content ?? [];
  const total = query.data?.totalElements ?? 0;
  const totalPages = query.data?.totalPages ?? 0;
  const loadError = query.isError ? memberErrorKey(query.error) : null;
  const filterCount = activeFilterCount(effective);

  const apply = (next: MemberFilters) => setParams(toSearchParams(next), { replace: true });

  // Debounced text -> URL (only when it really differs, so back/forward navigation does not loop).
  useEffect(() => {
    if (debouncedText.trim() !== filters.q.trim()) apply(withFilter(filters, { q: debouncedText }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedText]);

  const hasCriteria = filterCount > 0 || filters.q.trim() !== '';
  const reset = () => {
    setDraft(null);
    apply({ ...EMPTY_FILTERS, sort: filters.sort });
  };

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
          value={searchText}
          onChange={(e) => setDraft(e.currentTarget.value)}
          style={{ flex: '1 1 16rem', maxWidth: '28rem' }}
          styles={{ input: { minHeight: TOUCH } }}
        />
        {!isDesktop && (
          <Button mih={TOUCH} variant="default" onClick={() => setDrawerOpen(true)}>
            {t('members.directory.filtersOpen')}
            {filterCount > 0 ? ` (${filterCount})` : ''}
          </Button>
        )}
        {canCreate && (
          <Button mih={TOUCH} onClick={() => setCreating(true)}>
            {t('members.add')}
          </Button>
        )}
      </Group>

      {isDesktop && (
        <Card withBorder padding="md">
          <MemberFiltersPanel filters={effective} onChange={apply} canSeeArchived={canSeeArchived} />
        </Card>
      )}
      <Drawer
        opened={!isDesktop && drawerOpen}
        onClose={() => setDrawerOpen(false)}
        position="bottom"
        size="90%"
        title={t('members.directory.filtersTitle')}
      >
        <Stack gap="md">
          <MemberFiltersPanel filters={effective} onChange={apply} canSeeArchived={canSeeArchived} />
          <Group grow>
            <Button mih={TOUCH} variant="default" onClick={reset}>
              {t('members.directory.reset')}
            </Button>
            <Button mih={TOUCH} onClick={() => setDrawerOpen(false)}>
              {t('members.directory.done')}
            </Button>
          </Group>
        </Stack>
      </Drawer>

      {query.isLoading && <Loader size="sm" />}
      {loadError && <Alert color="red">{t(loadError === 'problems.UNKNOWN' ? 'members.loadFailed' : loadError)}</Alert>}
      {query.data && total === 0 && !hasCriteria && <Text c="dimmed">{t('members.empty')}</Text>}
      {query.data && total === 0 && hasCriteria && (
        <Group gap="sm">
          <Text c="dimmed">{t('members.nothingFound')}</Text>
          <Button variant="subtle" mih={TOUCH} onClick={reset}>
            {t('members.directory.reset')}
          </Button>
        </Group>
      )}
      {total > 0 && (
        <Text size="sm" c="dimmed">
          {t('members.count', { count: total })}
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

      {totalPages > 1 && (
        <Pagination
          total={totalPages}
          value={filters.page + 1}
          onChange={(p) => apply({ ...filters, page: p - 1 })}
          siblings={isDesktop ? 1 : 0}
          aria-label={t('members.directory.pagination')}
          styles={{ control: { minHeight: TOUCH, minWidth: TOUCH } }}
        />
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
