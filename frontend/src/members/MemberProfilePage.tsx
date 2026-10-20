import { Accordion, Alert, Anchor, Badge, Button, Card, Group, Loader, SegmentedControl, Stack, Text, Title } from '@mantine/core';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useParams } from 'react-router-dom';
import { EntityHistory } from '../audit';
import { formatKyivDateTime } from '../audit/kyivTime';
import { useAuth, usePermission } from '../auth/CurrentUser';
import { isLanguage } from '../i18n';
import { usePageTitle } from '../layout/pageTitle';
import { MemberActionModal, type MemberAction } from './MemberActionModal';
import { MemberAvatar } from './MemberAvatar';
import { MemberFormModal } from './MemberFormModal';
import { MemberOrgSections } from './MemberOrgSections';
import type { Member } from './memberApi';
import { formatDate, fullName, memberFacultyName, telegramLink } from './memberFormat';
import { memberErrorKey } from './memberProblems';
import { TemporaryPasswordModal } from './TemporaryPasswordModal';
import { useMember } from './useMembers';

const TOUCH = 44;
const MEMBERS_EDIT = 'members.edit';
const MEMBERS_ARCHIVE = 'members.archive';
/** Entity type under which the members module audits profile changes. */
const AUDIT_ENTITY = 'MemberProfile';

type Dialog =
  | { kind: 'form'; mode: 'edit' | 'self' }
  | { kind: 'action'; action: MemberAction }
  | { kind: 'password'; password: string }
  | null;

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Text component="div" style={{ overflowWrap: 'anywhere' }}>
        {children}
      </Text>
    </div>
  );
}

function Detail({ member }: { member: Member }) {
  const { t, i18n } = useTranslation();
  const link = telegramLink(member.telegram);
  const faculty = member.faculty;
  return (
    <Card withBorder padding="md">
      <Stack gap="sm">
        <Field label={t('faculties.select.label')}>
          {faculty ? (
            <>
              {memberFacultyName(faculty, i18n.language)}
              {!faculty.active && (
                <Text span c="dimmed">
                  {' '}
                  ({t('members.facultyArchived')})
                </Text>
              )}
            </>
          ) : (
            '—'
          )}
        </Field>
        <Field label={t('members.studyGroup')}>{member.studyGroup || '—'}</Field>
        <Field label={t('members.telegram')}>
          {link ? (
            <Anchor href={link} target="_blank" rel="noopener noreferrer">
              @{member.telegram.replace(/^@/, '')}
            </Anchor>
          ) : (
            member.telegram || '—'
          )}
        </Field>
        <Field label={t('members.joinedAt')}>{formatDate(member.joinedAt) || '—'}</Field>
        <Field label={t('members.login')}>{member.login}</Field>
        <Field label={t('members.bio')}>
          {member.bio ? <span style={{ whiteSpace: 'pre-wrap' }}>{member.bio}</span> : <Text span c="dimmed">{t('members.bioEmpty')}</Text>}
        </Field>
        {member.createdAt && <Field label={t('members.createdAt')}>{formatKyivDateTime(member.createdAt, i18n.language)}</Field>}
        {member.updatedAt && <Field label={t('members.updatedAt')}>{formatKyivDateTime(member.updatedAt, i18n.language)}</Field>}
        {member.archived && member.archivedAt && (
          <Field label={t('members.archivedAt')}>{formatKyivDateTime(member.archivedAt, i18n.language)}</Field>
        )}
      </Stack>
    </Card>
  );
}

/** Interface-language setting of the signed-in user, persisted to the account profile. */
function LanguageSetting() {
  const { t, i18n } = useTranslation();
  const { changeLocale } = useAuth();
  return (
    <Card withBorder padding="md">
      <Stack gap="xs">
        <Text fw={600}>{t('members.languageSetting')}</Text>
        <Text size="sm" c="dimmed">
          {t('members.languageHint')}
        </Text>
        <SegmentedControl
          value={isLanguage(i18n.language) ? i18n.language : 'uk'}
          onChange={(v) => {
            if (isLanguage(v)) void changeLocale(v);
          }}
          data={[
            { value: 'uk', label: t('common.languageUk') },
            { value: 'en', label: t('common.languageEn') },
          ]}
        />
      </Stack>
    </Card>
  );
}

/** Profile of one member; archived members stay viewable. Actions are gated by permission (backend enforces). */
export function MemberProfilePage() {
  const { t } = useTranslation();
  usePageTitle('members.profileTitle');
  const { id = '' } = useParams();
  const { user } = useAuth();
  const canEdit = usePermission(MEMBERS_EDIT);
  const canArchive = usePermission(MEMBERS_ARCHIVE);
  const [dialog, setDialog] = useState<Dialog>(null);
  const query = useMember(id);
  const member = query.data;
  const isSelf = !!member && user?.id === member.id;

  if (query.isLoading) return <Loader size="sm" />;
  if (query.isError || !member) {
    return (
      <Stack gap="md">
        <Anchor component={Link} to="/members">
          {t('members.backToList')}
        </Anchor>
        <Alert color="red">{t(query.isError ? memberErrorKey(query.error) : 'problems.NOT_FOUND')}</Alert>
      </Stack>
    );
  }

  const active = !member.archived;
  const showHrEdit = canEdit && active;
  const showReset = canEdit && active;
  const showArchive = canArchive && active && !isSelf;
  const showRestore = canArchive && member.archived;
  const name = fullName(member);

  return (
    <Stack gap="md">
      <Anchor component={Link} to="/members" style={{ minHeight: TOUCH, display: 'inline-flex', alignItems: 'center' }}>
        {t('members.backToList')}
      </Anchor>

      <Group wrap="nowrap" align="center" gap="md">
        <MemberAvatar member={member} size={80} />
        <Stack gap={4} style={{ minWidth: 0 }}>
          <Title order={1} size="h2" style={{ overflowWrap: 'anywhere' }}>
            {name}
          </Title>
          {member.archived && (
            <Badge color="gray" variant="light" w="fit-content">
              {t('members.archivedBadge')}
            </Badge>
          )}
        </Stack>
      </Group>

      {member.archived && <Alert color="yellow">{t('members.archivedNotice')}</Alert>}

      {(showHrEdit || (isSelf && active) || showReset || showArchive || showRestore) && (
        <Group gap="xs" wrap="wrap">
          {showHrEdit && (
            <Button mih={TOUCH} onClick={() => setDialog({ kind: 'form', mode: 'edit' })}>
              {t('members.edit')}
            </Button>
          )}
          {isSelf && active && (
            <Button
              mih={TOUCH}
              variant={showHrEdit ? 'default' : 'filled'}
              onClick={() => setDialog({ kind: 'form', mode: 'self' })}
            >
              {t('members.editSelf')}
            </Button>
          )}
          {showReset && (
            <Button mih={TOUCH} variant="default" onClick={() => setDialog({ kind: 'action', action: 'reset' })}>
              {t('members.reset.open')}
            </Button>
          )}
          {showArchive && (
            <Button mih={TOUCH} variant="default" onClick={() => setDialog({ kind: 'action', action: 'archive' })}>
              {t('members.archive.open')}
            </Button>
          )}
          {showRestore && (
            <Button mih={TOUCH} onClick={() => setDialog({ kind: 'action', action: 'restore' })}>
              {t('members.restore.open')}
            </Button>
          )}
        </Group>
      )}

      <Detail member={member} />
      {isSelf && <LanguageSetting />}
      <MemberOrgSections userId={member.id} />

      <Accordion variant="contained" multiple={false}>
        <Accordion.Item value="history">
          <Accordion.Control style={{ minHeight: TOUCH }}>{t('members.history')}</Accordion.Control>
          <Accordion.Panel>
            <EntityHistory entityType={AUDIT_ENTITY} entityId={member.id} />
          </Accordion.Panel>
        </Accordion.Item>
      </Accordion>

      {dialog?.kind === 'form' && (
        <MemberFormModal mode={dialog.mode} member={member} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'action' && (
        <MemberActionModal
          action={dialog.action}
          member={member}
          onClose={() => setDialog(null)}
          onPasswordReset={(password) => setDialog({ kind: 'password', password })}
        />
      )}
      {dialog?.kind === 'password' && (
        <TemporaryPasswordModal password={dialog.password} memberName={name} onClose={() => setDialog(null)} />
      )}
    </Stack>
  );
}

/** `/profile`: the signed-in user's own profile. */
export function MyProfileRedirect() {
  const { user } = useAuth();
  return user ? <Navigate to={`/members/${user.id}`} replace /> : null;
}
