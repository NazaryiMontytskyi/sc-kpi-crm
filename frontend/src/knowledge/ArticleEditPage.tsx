import { Alert, Button, Group, Loader, Stack, Switch, TagsInput, TextInput, Title } from '@mantine/core';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { usePermission } from '../auth/CurrentUser';
import { usePageTitle } from '../layout/pageTitle';
import {
  MAX_TAGS,
  MAX_TAG_LENGTH,
  MAX_TITLE_LENGTH,
  articleErrorKey,
  clearDraft,
  draftIsUsable,
  formFromArticle,
  isVersionConflict,
  loadDraft,
  saveDraft,
  toArticleInput,
  validateTitle,
  type ArticleFormState,
} from './articleLogic';
import { ArticleEditor } from './editor/ArticleEditor';
import { EMPTY_DOC, KB_EDIT, KB_RESTRICTED_VIEW, getArticle, type Article, type RichDoc } from './knowledgeApi';
import { KnowledgeLayout } from './KnowledgeLayout';
import { useArticle, useArticleMutations } from './useKnowledge';

const DRAFT_DELAY_MS = 1000;

interface FormProps {
  /** The article being edited; `null` creates a new one under `parentId`. */
  article: Article | null;
  parentId: string | null;
  onReloaded: () => void;
}

function ArticleForm({ article, parentId, onReloaded }: FormProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const canMarkRestricted = usePermission(KB_RESTRICTED_VIEW);
  const { create, update } = useArticleMutations();

  const [initial] = useState(() => {
    if (!article) return { form: { title: '', content: EMPTY_DOC, tags: [], restricted: false } as ArticleFormState, restored: false };
    const draft = loadDraft(window.localStorage, article.id);
    return draftIsUsable(draft, article)
      ? { form: draft.form, restored: true }
      : { form: formFromArticle(article), restored: false };
  });
  const [restoredDraft, setRestoredDraft] = useState(initial.restored);
  const [form, setForm] = useState<ArticleFormState>(initial.form);
  // The editor mounts once with this content; a "discard draft" remounts it through `editorKey`.
  const [editorKey, setEditorKey] = useState(0);
  const [initialDoc, setInitialDoc] = useState<RichDoc>(initial.form.content);
  const [dirty, setDirty] = useState(false);
  const [titleTouched, setTitleTouched] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  // Local draft: unsaved work survives a reload or a failed save. Never sent to the server (every save is a version).
  useEffect(() => {
    if (!article || !dirty) return;
    const timer = setTimeout(
      () => saveDraft(window.localStorage, article.id, { baseVersion: article.version, form, savedAt: Date.now() }),
      DRAFT_DELAY_MS,
    );
    return () => clearTimeout(timer);
  }, [article, dirty, form]);

  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const change = (patch: Partial<ArticleFormState>) => {
    setForm((f) => ({ ...f, ...patch }));
    setDirty(true);
  };

  const finish = (saved: Article) => {
    if (article) clearDraft(window.localStorage, article.id);
    setDirty(false);
    navigate(`/knowledge/${saved.id}`);
  };

  const save = async (expectedVersion?: number) => {
    setTitleTouched(true);
    if (!validateTitle(form.title)) return;
    setError(null);
    setConflict(false);
    setBusy(true);
    try {
      const input = toArticleInput(form);
      const saved = article
        ? await update.mutateAsync({ id: article.id, expectedVersion: expectedVersion ?? article.version, input })
        : await create.mutateAsync({ input, parentId });
      finish(saved);
    } catch (e) {
      if (isVersionConflict(e)) setConflict(true);
      else setError(e);
    } finally {
      setBusy(false);
    }
  };

  /** Conflict resolution: take the latest version from the server and drop local changes. */
  const reloadLatest = async () => {
    if (!article) return;
    clearDraft(window.localStorage, article.id);
    setDirty(false);
    onReloaded();
  };

  /** Conflict resolution: keep my content and save it on top of the latest version. */
  const overwrite = async () => {
    if (!article) return;
    setBusy(true);
    try {
      const latest = await getArticle(article.id);
      setBusy(false);
      await save(latest.version);
    } catch (e) {
      setBusy(false);
      setError(e);
    }
  };

  const discardDraft = () => {
    if (!article) return;
    clearDraft(window.localStorage, article.id);
    const original = formFromArticle(article);
    setForm(original);
    setInitialDoc(original.content);
    setEditorKey((k) => k + 1);
    setDirty(false);
    setRestoredDraft(false);
  };

  const titleError = titleTouched && !validateTitle(form.title) ? t('knowledge.form.titleRequired') : undefined;

  return (
    <Stack gap="md">
      <Title order={1} size="h2">
        {article ? t('knowledge.form.editTitle') : t('knowledge.form.newTitle')}
      </Title>

      {restoredDraft && (
        <Alert color="yellow" title={t('knowledge.draft.restored')}>
          <Button variant="default" size="compact-md" onClick={discardDraft}>
            {t('knowledge.draft.discard')}
          </Button>
        </Alert>
      )}

      <TextInput
        label={t('knowledge.form.title')}
        value={form.title}
        maxLength={MAX_TITLE_LENGTH}
        required
        error={titleError}
        onChange={(e) => change({ title: e.currentTarget.value })}
        onBlur={() => setTitleTouched(true)}
      />

      <ArticleEditor key={editorKey} initialContent={initialDoc} label={t('knowledge.form.content')} onChange={(doc) => change({ content: doc })} />

      <TagsInput
        label={t('knowledge.form.tags')}
        description={t('knowledge.form.tagsHint', { max: MAX_TAGS })}
        value={form.tags}
        maxTags={MAX_TAGS}
        onChange={(tags) => change({ tags: tags.map((x) => x.slice(0, MAX_TAG_LENGTH)) })}
        splitChars={[',']}
      />
      {canMarkRestricted && (
        <Switch
          label={t('knowledge.form.restricted')}
          description={t('knowledge.form.restrictedHint')}
          checked={form.restricted}
          onChange={(e) => change({ restricted: e.currentTarget.checked })}
        />
      )}

      {conflict && (
        <Alert color="orange" title={t('knowledge.conflict.title')} data-testid="conflict-alert">
          <Stack gap="xs">
            <span>{t('knowledge.conflict.body')}</span>
            <Group gap="xs">
              <Button variant="default" mih={44} onClick={reloadLatest}>
                {t('knowledge.conflict.reload')}
              </Button>
              <Button mih={44} loading={busy} onClick={overwrite}>
                {t('knowledge.conflict.overwrite')}
              </Button>
              <Button variant="subtle" mih={44} onClick={() => setConflict(false)}>
                {t('knowledge.conflict.keepEditing')}
              </Button>
            </Group>
          </Stack>
        </Alert>
      )}
      {error !== null && <Alert color="red">{t(articleErrorKey(error))}</Alert>}

      <Group>
        <Button mih={44} loading={busy && !conflict} onClick={() => void save()}>
          {t('knowledge.form.save')}
        </Button>
        <Button
          component={Link}
          to={article ? `/knowledge/${article.id}` : parentId ? `/knowledge/${parentId}` : '/knowledge'}
          variant="default"
          mih={44}
        >
          {t('knowledge.cancel')}
        </Button>
      </Group>
    </Stack>
  );
}

/** Edit mode: create a page (`/knowledge/new?parent=`) or edit one (`/knowledge/:id/edit`). */
export function ArticleEditPage() {
  const { t } = useTranslation();
  usePageTitle('nav.knowledge');
  const { id } = useParams();
  const [params] = useSearchParams();
  const canEdit = usePermission(KB_EDIT);
  const query = useArticle(id);
  const [reloads, setReloads] = useState(0);

  const reload = async () => {
    await query.refetch();
    setReloads((n) => n + 1);
  };

  return (
    <KnowledgeLayout activeId={id}>
      {!canEdit && <Alert color="red">{t('problems.FORBIDDEN')}</Alert>}
      {canEdit && id && query.isLoading && <Loader size="sm" />}
      {canEdit && id && query.isError && <Alert color="red">{t(articleErrorKey(query.error))}</Alert>}
      {canEdit && id && query.data?.archived && <Alert color="yellow">{t('knowledge.archivedNotice')}</Alert>}
      {canEdit && id && query.data && !query.data.archived && (
        <ArticleForm key={`${query.data.id}-${query.data.version}-${reloads}`} article={query.data} parentId={null} onReloaded={() => void reload()} />
      )}
      {canEdit && !id && <ArticleForm article={null} parentId={params.get('parent')} onReloaded={() => undefined} />}
    </KnowledgeLayout>
  );
}
