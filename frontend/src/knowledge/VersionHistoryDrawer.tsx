import { Alert, Badge, Button, Drawer, Group, Loader, Modal, Stack, Text, Title, UnstyledButton } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { formatKyivDateTime } from '../audit/kyivTime';
import { articleErrorKey } from './articleLogic';
import { ArticleView } from './editor/ArticleView';
import { useArticleVersion, useArticleVersions, useRestoreVersion } from './useKnowledge';

interface Props {
  articleId: string;
  currentVersion: number;
  /** Restoring needs kb.edit and a non-archived article. */
  canRestore: boolean;
  onClose: () => void;
}

/** Version list of an article with a read-only preview and a confirmed restore. */
export function VersionHistoryDrawer({ articleId, currentVersion, canRestore, onClose }: Props) {
  const { t, i18n } = useTranslation();
  const [selected, setSelected] = useState<number | null>(null);
  const [confirming, setConfirming] = useState(false);
  const list = useArticleVersions(articleId, true);
  const preview = useArticleVersion(articleId, selected);
  const restore = useRestoreVersion();

  const doRestore = () => {
    if (selected === null) return;
    restore.mutate(
      { id: articleId, versionNumber: selected, expectedVersion: currentVersion },
      {
        onSuccess: () => {
          setConfirming(false);
          setSelected(null);
          onClose();
        },
        onError: () => setConfirming(false),
      },
    );
  };

  return (
    <Drawer opened onClose={onClose} position="right" size="lg" title={t('knowledge.versions.title')}>
      <Stack gap="md">
        {restore.isError && <Alert color="red">{t(articleErrorKey(restore.error))}</Alert>}

        {selected === null && (
          <>
            {list.isLoading && <Loader size="sm" />}
            {list.isError && <Alert color="red">{t(articleErrorKey(list.error))}</Alert>}
            {list.data?.length === 0 && <Text c="dimmed">{t('knowledge.versions.empty')}</Text>}
            <Stack gap="xs" component="ul" p={0} m={0} style={{ listStyle: 'none' }}>
              {list.data?.map((v) => (
                <li key={v.versionNumber}>
                  <UnstyledButton
                    onClick={() => {
                      restore.reset();
                      setSelected(v.versionNumber);
                    }}
                    w="100%"
                    mih={44}
                    p="xs"
                    style={{ border: '1px solid var(--mantine-color-default-border)', borderRadius: 'var(--mantine-radius-sm)' }}
                  >
                    <Group justify="space-between" wrap="nowrap" gap="xs">
                      <Stack gap={0} style={{ minWidth: 0 }}>
                        <Text fw={500} size="sm">
                          {t('knowledge.versions.version', { n: v.versionNumber })}
                        </Text>
                        <Text size="sm" truncate>
                          {v.title}
                        </Text>
                        <Text size="xs" c="dimmed">
                          {formatKyivDateTime(v.editedAt, i18n.language)}
                        </Text>
                      </Stack>
                      {v.versionNumber === currentVersion && <Badge variant="light">{t('knowledge.versions.current')}</Badge>}
                    </Group>
                  </UnstyledButton>
                </li>
              ))}
            </Stack>
          </>
        )}

        {selected !== null && (
          <Stack gap="md">
            <Button variant="default" mih={44} onClick={() => setSelected(null)} style={{ alignSelf: 'flex-start' }}>
              {t('knowledge.versions.back')}
            </Button>
            {preview.isLoading && <Loader size="sm" />}
            {preview.isError && <Alert color="red">{t(articleErrorKey(preview.error))}</Alert>}
            {preview.data && (
              <>
                <Text size="xs" c="dimmed">
                  {t('knowledge.versions.preview')}: {t('knowledge.versions.version', { n: preview.data.versionNumber })},{' '}
                  {formatKyivDateTime(preview.data.editedAt, i18n.language)}
                </Text>
                <Title order={2} size="h3" style={{ overflowWrap: 'anywhere' }}>
                  {preview.data.title}
                </Title>
                <ArticleView content={preview.data.content} />
                {canRestore && selected !== currentVersion && (
                  <Button mih={44} onClick={() => setConfirming(true)}>
                    {t('knowledge.versions.restore')}
                  </Button>
                )}
              </>
            )}
          </Stack>
        )}
      </Stack>

      {confirming && selected !== null && (
        <Modal opened onClose={() => setConfirming(false)} title={t('knowledge.versions.confirmTitle', { n: selected })}>
          <Stack>
            <Text size="sm">{t('knowledge.versions.confirmBody', { n: selected })}</Text>
            <Group justify="flex-end">
              <Button variant="default" mih={44} onClick={() => setConfirming(false)}>
                {t('knowledge.cancel')}
              </Button>
              <Button mih={44} loading={restore.isPending} onClick={doRestore}>
                {t('knowledge.versions.confirm')}
              </Button>
            </Group>
          </Stack>
        </Modal>
      )}
    </Drawer>
  );
}
