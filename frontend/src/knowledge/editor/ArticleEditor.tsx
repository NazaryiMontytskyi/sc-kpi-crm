import { Alert, Button, Group, Loader, Modal, Stack, Text, TextInput, Typography } from '@mantine/core';
import type { Editor } from '@tiptap/core';
import { EditorContent, useEditor } from '@tiptap/react';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { problemMessage } from '../../api/problem';
import { listMembers } from '../../members/memberApi';
import { listTasks } from '../../tasks/tasksApi';
import { getArticleTree, uploadArticleFile, type RichDoc } from '../knowledgeApi';
import { KB_KEY } from '../useKnowledge';
import {
  FILE_KIND_ACCEPT,
  applyLink,
  fileMatchesKind,
  filterBlocks,
  insertFileAttachment,
  insertImage,
  insertPdf,
  type BlockCommand,
  type BlockHost,
  type FileKind,
} from './blocks';
import { createExtensions } from './extensions';
import { buildMentionCandidates, flattenTree, type MentionCandidate } from './mentionCandidates';
import { SuggestionBridge } from './suggestionBridge';
import { SuggestionMenu } from './SuggestionMenu';
import { Toolbar } from './Toolbar';
import './editor.css';

interface Props {
  /** Initial document. To replace the content from outside, remount the component with a new `key`. */
  initialContent: RichDoc;
  onChange: (doc: RichDoc) => void;
  label: string;
}

const STALE_MS = 60_000;

/**
 * Full TipTap editor of the knowledge base: toolbar, slash menu, @mentions, uploads.
 * Stores and emits ProseMirror JSON.
 */
export function ArticleEditor({ initialContent, onChange, label }: Props) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const tRef = useRef(t);
  const hostRef = useRef<BlockHost | null>(null);
  const onChangeRef = useRef(onChange);
  useEffect(() => {
    tRef.current = t;
    onChangeRef.current = onChange;
  });

  const slashBridge = useMemo(() => new SuggestionBridge<BlockCommand>(), []);
  const mentionBridge = useMemo(() => new SuggestionBridge<MentionCandidate>(), []);

  const getMentionItems = useCallback(
    async (query: string) => {
      const [members, tree, tasks] = await Promise.all([
        qc.fetchQuery({ queryKey: ['members', 'mention-pool'], queryFn: () => listMembers(false), staleTime: STALE_MS }).catch(() => []),
        qc.fetchQuery({ queryKey: [...KB_KEY, 'tree'], queryFn: getArticleTree, staleTime: STALE_MS }).catch(() => []),
        qc
          .fetchQuery({
            queryKey: ['tasks', 'mention-pool'],
            queryFn: () => listTasks({ labels: [], includeArchived: false, sortBy: 'updatedAt', direction: 'desc', page: 0, size: 100 }),
            staleTime: STALE_MS,
          })
          .then((p) => p.items)
          .catch(() => []),
      ]);
      return buildMentionCandidates({ members, articles: flattenTree(tree), tasks }, query);
    },
    [qc],
  );

  const extensions = useMemo(
    () =>
      // The callbacks read the refs later (on typing), never during render.
      // eslint-disable-next-line react-hooks/refs
      createExtensions({
        slash: {
          bridge: slashBridge,
          getItems: (query) => filterBlocks(query, (key) => tRef.current(`knowledge.blocks.${key}`)),
          run: (item, editor) => hostRef.current && item.run(editor, hostRef.current),
        },
        mention: { bridge: mentionBridge, getItems: getMentionItems },
      }),
    [slashBridge, mentionBridge, getMentionItems],
  );

  const editor = useEditor({
    extensions,
    content: initialContent,
    immediatelyRender: false,
    editorProps: {
      attributes: { 'aria-label': label, role: 'textbox', 'aria-multiline': 'true', class: 'sc-editor-content' },
    },
    onUpdate: ({ editor: e }) => onChangeRef.current(e.getJSON() as RichDoc),
  });

  // ---- link dialog and uploads -------------------------------------------------------------
  const [linkOpen, setLinkOpen] = useState(false);
  const [linkUrl, setLinkUrl] = useState('');
  const [linkInvalid, setLinkInvalid] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const kindRef = useRef<FileKind>('file');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [accept, setAccept] = useState('');

  const host = useMemo<BlockHost>(
    () => ({
      askLink: () => {
        const href = (editor?.getAttributes('link').href as string | undefined) ?? '';
        setLinkUrl(href);
        setLinkInvalid(false);
        setLinkOpen(true);
      },
      pickFile: (kind) => {
        kindRef.current = kind;
        setAccept(FILE_KIND_ACCEPT[kind]);
        // The accept attribute is applied on the next render; open the picker after it.
        setTimeout(() => fileInputRef.current?.click(), 0);
      },
    }),
    [editor],
  );
  useEffect(() => {
    hostRef.current = host;
  }, [host]);

  const onFilePicked = async (e: Editor, file: File | null) => {
    if (!file) return;
    const kind = kindRef.current;
    setUploadError(null);
    if (!fileMatchesKind(kind, file)) {
      setUploadError(kind === 'image' ? 'knowledge.upload.notImage' : 'knowledge.upload.notPdf');
      return;
    }
    setUploading(true);
    try {
      const uploaded = await uploadArticleFile(file);
      if (kind === 'image') insertImage(e, uploaded);
      else if (kind === 'pdf') insertPdf(e, uploaded);
      else insertFileAttachment(e, uploaded);
    } catch (err) {
      setUploadError(problemMessage(err).key);
    } finally {
      setUploading(false);
    }
  };

  const submitLink = () => {
    if (!editor) return;
    if (linkUrl.trim() === '' && editor.isActive('link')) {
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
      setLinkOpen(false);
      return;
    }
    if (!applyLink(editor, linkUrl)) {
      setLinkInvalid(true);
      return;
    }
    setLinkOpen(false);
  };

  if (!editor) return <Loader size="sm" />;

  return (
    <Stack gap="xs">
      <Toolbar editor={editor} host={host} />
      {uploading && (
        <Group gap="xs">
          <Loader size="xs" />
          <Text size="sm">{t('knowledge.upload.uploading')}</Text>
        </Group>
      )}
      {uploadError && (
        <Alert color="red" withCloseButton onClose={() => setUploadError(null)}>
          {t(uploadError)}
        </Alert>
      )}
      <Typography className="sc-editor">
        <EditorContent editor={editor} />
      </Typography>

      <input
        ref={fileInputRef}
        type="file"
        hidden
        accept={accept}
        data-testid="editor-file-input"
        onChange={(ev) => {
          const file = ev.currentTarget.files?.[0] ?? null;
          ev.currentTarget.value = '';
          void onFilePicked(editor, file);
        }}
      />

      <SuggestionMenu
        bridge={slashBridge}
        label={t('knowledge.slash.label')}
        empty={t('knowledge.slash.empty')}
        testId="slash-menu"
        itemKey={(b) => b.key}
        renderItem={(b) => <Text size="sm">{t(`knowledge.blocks.${b.key}`)}</Text>}
      />
      <SuggestionMenu
        bridge={mentionBridge}
        label={t('knowledge.mention.label')}
        empty={t('knowledge.mention.empty')}
        testId="mention-menu"
        itemKey={(c) => `${c.entityType}:${c.id}`}
        renderItem={(c) => (
          <Group gap="xs" wrap="nowrap">
            <Text size="xs" c="dimmed" style={{ minWidth: 56 }}>
              {t(`knowledge.mention.${c.entityType}`)}
            </Text>
            <Text size="sm" truncate>
              {c.label}
            </Text>
          </Group>
        )}
      />

      <Modal opened={linkOpen} onClose={() => setLinkOpen(false)} title={t('knowledge.link.title')} fullScreen={false}>
        <Stack>
          <TextInput
            label={t('knowledge.link.url')}
            placeholder="https://"
            value={linkUrl}
            onChange={(e) => {
              setLinkUrl(e.currentTarget.value);
              setLinkInvalid(false);
            }}
            error={linkInvalid ? t('knowledge.link.invalid') : undefined}
            data-autofocus
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                submitLink();
              }
            }}
          />
          <Group justify="flex-end">
            <Button variant="default" mih={44} onClick={() => setLinkOpen(false)}>
              {t('knowledge.cancel')}
            </Button>
            <Button mih={44} onClick={submitLink}>
              {t('knowledge.link.apply')}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
