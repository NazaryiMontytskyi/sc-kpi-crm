import { Alert, Badge, Button, Card, Group, Loader, Modal, Stack, Switch, Table, Text, Title } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { problemMessage } from '../api/problem';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { FacultyFormModal } from './FacultyFormModal';
import { facultyName, type Faculty } from './facultyApi';
import { useFaculties, useFacultyMutations } from './useFaculties';

const TOUCH = 44;
/** Permission that guards every faculty mutation (the backend is authoritative). */
export const SETTINGS_MANAGE = 'settings.manage';

type ConfirmDialog = { kind: 'archive' | 'restore'; faculty: Faculty };
type Dialog = { kind: 'form'; faculty: Faculty | null } | ConfirmDialog | null;

function StatusBadge({ archived }: { archived: boolean }) {
  const { t } = useTranslation();
  return (
    <Badge color={archived ? 'gray' : 'green'} variant="light">
      {archived ? t('faculties.archived') : t('faculties.active')}
    </Badge>
  );
}

function RowActions({ faculty, onDialog }: { faculty: Faculty; onDialog: (d: Dialog) => void }) {
  const { t } = useTranslation();
  const canManage = usePermission(SETTINGS_MANAGE);
  if (!canManage) return null;
  return (
    <Group gap="xs" wrap="wrap">
      {!faculty.archived && (
        <Button variant="default" mih={TOUCH} onClick={() => onDialog({ kind: 'form', faculty })}>
          {t('faculties.edit')}
        </Button>
      )}
      {faculty.archived ? (
        <Button variant="default" mih={TOUCH} onClick={() => onDialog({ kind: 'restore', faculty })}>
          {t('faculties.restore')}
        </Button>
      ) : (
        <Button variant="default" mih={TOUCH} onClick={() => onDialog({ kind: 'archive', faculty })}>
          {t('faculties.archive')}
        </Button>
      )}
    </Group>
  );
}

function ConfirmModal({ dialog, onClose }: { dialog: ConfirmDialog; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const { archive, restore } = useFacultyMutations();
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const isArchive = dialog.kind === 'archive';
  const mutation = isArchive ? archive : restore;
  const name = facultyName(dialog.faculty, i18n.language);
  return (
    <Modal opened onClose={onClose} centered title={t(isArchive ? 'faculties.archiveTitle' : 'faculties.restoreTitle')}>
      <Stack gap="md">
        {errorKey && <Alert color="red">{t(errorKey)}</Alert>}
        <Text>{t(isArchive ? 'faculties.archiveConfirm' : 'faculties.restoreConfirm', { name })}</Text>
        <Group justify="flex-end">
          <Button variant="default" mih={TOUCH} onClick={onClose}>
            {t('faculties.cancel')}
          </Button>
          <Button
            color={isArchive ? 'red' : undefined}
            mih={TOUCH}
            loading={mutation.isPending}
            onClick={() =>
              mutation.mutate(dialog.faculty.id, {
                onSuccess: onClose,
                onError: (e) => setErrorKey(problemMessage(e).key),
              })
            }
          >
            {t(isArchive ? 'faculties.archive' : 'faculties.restore')}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

/** Settings page for the faculty/institute dictionary. Mutations need `settings.manage`. */
export function FacultiesPage() {
  const { t, i18n } = useTranslation();
  usePageTitle('nav.settingsFaculties');
  const canManage = usePermission(SETTINGS_MANAGE);
  const [showArchived, setShowArchived] = useState(false);
  const [dialog, setDialog] = useState<Dialog>(null);
  const isDesktop = useMediaQuery('(min-width: 48em)');
  const query = useFaculties(showArchived);
  const faculties = query.data ?? [];
  const activeFaculties = faculties.filter((f) => !f.archived);
  const loadError = query.isError ? problemMessage(query.error).key : null;

  return (
    <Stack gap="md">
      <Title order={1} size="h2">
        {t('faculties.title')}
      </Title>
      <Text c="dimmed">{t('faculties.description')}</Text>
      {canManage && (
        <Group justify="space-between" align="center">
          <Switch
            label={t('faculties.showArchived')}
            checked={showArchived}
            onChange={(e) => setShowArchived(e.currentTarget.checked)}
            styles={{ root: { minHeight: TOUCH, display: 'flex', alignItems: 'center' } }}
          />
          <Button mih={TOUCH} onClick={() => setDialog({ kind: 'form', faculty: null })}>
            {t('faculties.add')}
          </Button>
        </Group>
      )}

      {query.isLoading && <Loader size="sm" />}
      {loadError && <Alert color="red">{t(loadError === 'problems.UNKNOWN' ? 'faculties.loadFailed' : loadError)}</Alert>}
      {query.isSuccess && faculties.length === 0 && <Text c="dimmed">{t('faculties.empty')}</Text>}

      {faculties.length > 0 && isDesktop && (
        <Table withTableBorder highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('faculties.code')}</Table.Th>
              <Table.Th>{t('faculties.nameUk')}</Table.Th>
              <Table.Th>{t('faculties.nameEn')}</Table.Th>
              <Table.Th>{t('faculties.status')}</Table.Th>
              {canManage && <Table.Th>{t('faculties.actions')}</Table.Th>}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {faculties.map((f) => (
              <Table.Tr key={f.id}>
                <Table.Td>{f.code}</Table.Td>
                <Table.Td>{f.nameUk}</Table.Td>
                <Table.Td>{f.nameEn}</Table.Td>
                <Table.Td>
                  <StatusBadge archived={f.archived} />
                </Table.Td>
                {canManage && (
                  <Table.Td>
                    <RowActions faculty={f} onDialog={setDialog} />
                  </Table.Td>
                )}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {faculties.length > 0 && !isDesktop && (
        <Stack gap="sm">
          {faculties.map((f) => (
            <Card key={f.id} withBorder padding="md" data-testid="faculty-card">
              <Stack gap="xs">
                <Group justify="space-between" wrap="nowrap" align="flex-start">
                  <Text fw={600}>{facultyName(f, i18n.language)}</Text>
                  <StatusBadge archived={f.archived} />
                </Group>
                <Text size="sm" c="dimmed">
                  {t('faculties.code')}: {f.code}
                </Text>
                <RowActions faculty={f} onDialog={setDialog} />
              </Stack>
            </Card>
          ))}
        </Stack>
      )}

      {canManage && dialog?.kind === 'form' && (
        <FacultyFormModal faculty={dialog.faculty} known={activeFaculties} onClose={() => setDialog(null)} />
      )}
      {canManage && (dialog?.kind === 'archive' || dialog?.kind === 'restore') && (
        <ConfirmModal dialog={dialog} onClose={() => setDialog(null)} />
      )}
    </Stack>
  );
}
