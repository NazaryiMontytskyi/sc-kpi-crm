import { Alert, Anchor, Badge, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { MIN_QUERY_LENGTH } from './searchApi';
import { hitLink, visibleGroups } from './searchLogic';
import { useSearch } from './useSearch';

interface Props {
  query: string;
  limit: number;
  /** Called when a result link is followed (e.g. to close the dialog). */
  onNavigate?: () => void;
}

/** Search results grouped by type with counts, plus localized loading / empty / error states. */
export function SearchResults({ query, limit, onNavigate }: Props) {
  const { t } = useTranslation();
  const q = query.trim();
  const search = useSearch(q, limit);

  if (q.length < MIN_QUERY_LENGTH) return <Text c="dimmed">{t('search.hint', { count: MIN_QUERY_LENGTH })}</Text>;
  if (search.isError) return <Alert color="red">{t('search.failed')}</Alert>;
  if (!search.data) return <Loader size="sm" aria-label={t('search.loading')} />;

  const groups = visibleGroups(search.data);
  if (groups.length === 0) return <Text c="dimmed">{t('search.empty', { query: q })}</Text>;

  return (
    <Stack gap="lg">
      {groups.map((g) => (
        <Stack key={g.key} gap={4} component="section" aria-label={t(`search.groups.${g.key}`)}>
          <Group gap="xs">
            <Title order={4}>{t(`search.groups.${g.key}`)}</Title>
            <Badge variant="light" color="gray" aria-label={t('search.count', { count: g.total })}>
              {g.total}
            </Badge>
          </Group>
          {g.items.map((hit) => (
            <Anchor
              key={hit.id}
              component={Link}
              to={hitLink(g.key, hit)}
              onClick={onNavigate}
              underline="never"
              c="inherit"
              mih={44}
              py={6}
              style={{ display: 'block' }}
            >
              <Text fw={500} lineClamp={2}>
                {hit.title}
                {hit.restricted && (
                  <Badge ml="xs" size="xs" variant="outline" color="gray">
                    {t('search.restricted')}
                  </Badge>
                )}
              </Text>
              {hit.snippet && (
                <Text size="sm" c="dimmed" lineClamp={2}>
                  {hit.snippet}
                </Text>
              )}
            </Anchor>
          ))}
          {g.total > g.items.length && (
            <Text size="xs" c="dimmed">
              {t('search.more', { count: g.total - g.items.length })}
            </Text>
          )}
        </Stack>
      ))}
    </Stack>
  );
}
