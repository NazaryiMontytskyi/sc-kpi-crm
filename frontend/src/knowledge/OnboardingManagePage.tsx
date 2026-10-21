import { ActionIcon, Alert, Badge, Button, Card, Group, Loader, Select, Stack, Switch, Text, TextInput, Title } from '@mantine/core';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import { activeUnits } from '../org/orgTree';
import { useOrgTree } from '../org/useOrg';
import { KnowledgeLayout } from './KnowledgeLayout';
import { ONBOARDING_MANAGE, type OnboardingList } from './onboardingApi';
import { appendUnique, moveItem, onboardingErrorKey, removeAt, sameIds } from './onboardingLogic';
import { useArticles } from './useKnowledge';
import { useManagedOnboarding, useOnboardingMutations } from './useOnboarding';

const GENERAL = '__general__';
const MAX_TITLE = 200;

interface Option {
  value: string;
  label: string;
}

function validTitle(title: string): boolean {
  const s = title.trim();
  return s.length > 0 && s.length <= MAX_TITLE;
}

/** Ordered article picker: up/down buttons (touch friendly), remove, and an add-article select. */
function ArticleOrder({
  ids,
  titles,
  options,
  onChange,
}: {
  ids: string[];
  titles: ReadonlyMap<string, string>;
  options: Option[];
  onChange: (ids: string[]) => void;
}) {
  const { t } = useTranslation();
  const [adding, setAdding] = useState<string | null>(null);
  const available = options.filter((o) => !ids.includes(o.value));
  return (
    <Stack gap="xs">
      <Stack gap="xs" component="ol" m={0} pl={0} style={{ listStyle: 'none' }}>
        {ids.map((id, index) => (
          <Group key={id} component="li" wrap="nowrap" gap="xs" mih={44} data-testid="onboarding-item">
            <Text style={{ flex: '1 1 0', minWidth: 0, overflowWrap: 'anywhere' }}>
              {index + 1}. {titles.get(id) ?? id}
            </Text>
            <ActionIcon
              variant="default"
              size={44}
              aria-label={t('knowledge.onboarding.moveUp')}
              disabled={index === 0}
              onClick={() => onChange(moveItem(ids, index, index - 1))}
            >
              ↑
            </ActionIcon>
            <ActionIcon
              variant="default"
              size={44}
              aria-label={t('knowledge.onboarding.moveDown')}
              disabled={index === ids.length - 1}
              onClick={() => onChange(moveItem(ids, index, index + 1))}
            >
              ↓
            </ActionIcon>
            <ActionIcon variant="default" color="red" size={44} aria-label={t('knowledge.onboarding.remove')} onClick={() => onChange(removeAt(ids, index))}>
              ×
            </ActionIcon>
          </Group>
        ))}
      </Stack>
      <Group align="flex-end" wrap="nowrap" gap="xs">
        <Select
          style={{ flex: '1 1 0', minWidth: 0 }}
          label={t('knowledge.onboarding.addArticle')}
          placeholder={t('knowledge.onboarding.pickArticle')}
          data={available}
          value={adding}
          onChange={setAdding}
          searchable
          clearable
          nothingFoundMessage={t('knowledge.onboarding.noArticles')}
          comboboxProps={{ withinPortal: true }}
        />
        <Button
          variant="light"
          mih={36}
          disabled={!adding}
          onClick={() => {
            if (adding) onChange(appendUnique(ids, adding));
            setAdding(null);
          }}
        >
          {t('knowledge.onboarding.add')}
        </Button>
      </Group>
    </Stack>
  );
}

function useArticleOptions() {
  const articles = useArticles(undefined, false);
  return useMemo(() => {
    const options = (articles.data ?? []).map((a) => ({ value: a.id, label: a.title }));
    return { options, titles: new Map(options.map((o) => [o.value, o.label])) };
  }, [articles.data]);
}

function ListEditor({ list, options, knownTitles }: { list: OnboardingList; options: Option[]; knownTitles: ReadonlyMap<string, string> }) {
  const { t } = useTranslation();
  const { update, archive, restore } = useOnboardingMutations();
  const savedIds = useMemo(() => list.items.map((i) => i.articleId), [list.items]);
  const [title, setTitle] = useState(list.title);
  const [ids, setIds] = useState(savedIds);
  const titles = useMemo(() => {
    const map = new Map(knownTitles);
    list.items.forEach((i) => map.set(i.articleId, i.title));
    return map;
  }, [knownTitles, list.items]);
  const dirty = title.trim() !== list.title || !sameIds(ids, savedIds);
  const error = update.error ?? archive.error ?? restore.error;
  return (
    <Card withBorder padding="md" component="section" data-testid="managed-list" aria-label={list.title}>
      <Stack gap="sm">
        <Group gap="xs">
          <Badge variant="light">{list.orgUnitName ?? t('knowledge.onboarding.general')}</Badge>
          {list.archived && (
            <Badge variant="outline" color="gray">
              {t('knowledge.archivedBadge')}
            </Badge>
          )}
        </Group>
        <TextInput
          label={t('knowledge.onboarding.listTitle')}
          value={title}
          maxLength={MAX_TITLE}
          disabled={list.archived}
          onChange={(e) => setTitle(e.currentTarget.value)}
          error={!validTitle(title) ? t('knowledge.onboarding.titleRequired') : undefined}
        />
        {!list.archived && <ArticleOrder ids={ids} titles={titles} options={options} onChange={setIds} />}
        {error != null && <Alert color="red">{t(onboardingErrorKey(error))}</Alert>}
        <Group justify="flex-end">
          {list.archived ? (
            <Button variant="default" mih={44} loading={restore.isPending} onClick={() => restore.mutate(list.id)}>
              {t('knowledge.restore')}
            </Button>
          ) : (
            <>
              <Button variant="default" mih={44} loading={archive.isPending} onClick={() => archive.mutate(list.id)}>
                {t('knowledge.archive')}
              </Button>
              <Button
                mih={44}
                loading={update.isPending}
                disabled={!dirty || !validTitle(title)}
                onClick={() => update.mutate({ id: list.id, title: title.trim(), articleIds: ids })}
              >
                {t('knowledge.onboarding.save')}
              </Button>
            </>
          )}
        </Group>
      </Stack>
    </Card>
  );
}

function NewListForm({ options, taken }: { options: Option[]; taken: ReadonlySet<string> }) {
  const { t } = useTranslation();
  const tree = useOrgTree(false);
  const { create } = useOnboardingMutations();
  const [scope, setScope] = useState<string>(GENERAL);
  const [title, setTitle] = useState('');
  const [ids, setIds] = useState<string[]>([]);
  const scopes = useMemo(
    () => [
      { value: GENERAL, label: t('knowledge.onboarding.general') },
      ...activeUnits(tree.data ?? []).map((u) => ({ value: u.id, label: u.name })),
    ].filter((o) => !taken.has(o.value)),
    [tree.data, taken, t],
  );
  const effectiveScope = scopes.some((s) => s.value === scope) ? scope : (scopes[0]?.value ?? null);
  const titles = useMemo(() => new Map(options.map((o) => [o.value, o.label])), [options]);
  return (
    <Card withBorder padding="md" component="section" aria-label={t('knowledge.onboarding.newList')}>
      <Stack gap="sm">
        <Title order={2} size="h4">
          {t('knowledge.onboarding.newList')}
        </Title>
        <Select
          label={t('knowledge.onboarding.scope')}
          data={scopes}
          value={effectiveScope}
          onChange={(v) => v && setScope(v)}
          allowDeselect={false}
          comboboxProps={{ withinPortal: true }}
        />
        <TextInput
          label={t('knowledge.onboarding.listTitle')}
          value={title}
          maxLength={MAX_TITLE}
          onChange={(e) => setTitle(e.currentTarget.value)}
        />
        <ArticleOrder ids={ids} titles={titles} options={options} onChange={setIds} />
        {create.isError && <Alert color="red">{t(onboardingErrorKey(create.error))}</Alert>}
        <Group justify="flex-end">
          <Button
            mih={44}
            loading={create.isPending}
            disabled={!validTitle(title) || effectiveScope === null}
            onClick={() =>
              create.mutate(
                { title: title.trim(), orgUnitId: effectiveScope === GENERAL ? null : effectiveScope, articleIds: ids },
                {
                  onSuccess: () => {
                    setTitle('');
                    setIds([]);
                  },
                },
              )
            }
          >
            {t('knowledge.onboarding.create')}
          </Button>
        </Group>
      </Stack>
    </Card>
  );
}

/** Management of onboarding lists; needs onboarding.manage (the backend is authoritative). */
export function OnboardingManagePage() {
  const { t } = useTranslation();
  usePageTitle('knowledge.onboarding.manage');
  const canManage = usePermission(ONBOARDING_MANAGE);
  const [showArchived, setShowArchived] = useState(false);
  const query = useManagedOnboarding(showArchived);
  const { options, titles } = useArticleOptions();
  const taken = useMemo(
    () => new Set((query.data ?? []).filter((l) => !l.archived).map((l) => l.orgUnitId ?? GENERAL)),
    [query.data],
  );
  return (
    <KnowledgeLayout>
      <Group justify="space-between" align="center" gap="sm">
        <Title order={1} size="h2">
          {t('knowledge.onboarding.manage')}
        </Title>
        <Button component={Link} to="/knowledge/onboarding" variant="default" mih={44}>
          {t('knowledge.onboarding.title')}
        </Button>
      </Group>
      {!canManage && <Alert color="yellow">{t('knowledge.onboarding.noAccess')}</Alert>}
      {canManage && (
        <>
          <Switch
            label={t('knowledge.showArchived')}
            checked={showArchived}
            onChange={(e) => setShowArchived(e.currentTarget.checked)}
            styles={{ root: { minHeight: 44, display: 'flex', alignItems: 'center' } }}
          />
          {query.isLoading && <Loader size="sm" />}
          {query.isError && <Alert color="red">{t(onboardingErrorKey(query.error))}</Alert>}
          {query.data && query.data.length === 0 && <Text c="dimmed">{t('knowledge.onboarding.empty')}</Text>}
          <Stack gap="md">
            {(query.data ?? []).map((l) => (
              <ListEditor key={`${l.id}:${l.items.map((i) => i.articleId).join(',')}:${l.title}`} list={l} options={options} knownTitles={titles} />
            ))}
            <NewListForm options={options} taken={taken} />
          </Stack>
        </>
      )}
    </KnowledgeLayout>
  );
}
