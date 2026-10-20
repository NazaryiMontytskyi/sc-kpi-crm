import { ActionIcon, Group, Input, Paper } from '@mantine/core';
import type { Editor } from '@tiptap/core';
import Mention from '@tiptap/extension-mention';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

export type RichDoc = Record<string, unknown>;

interface Props {
  label: string;
  /** TipTap JSON document; `null`/`undefined` is an empty field. */
  value: RichDoc | null | undefined;
  /** Receives the JSON document, or `undefined` when the content is empty. */
  onChange: (doc: RichDoc | undefined) => void;
  disabled?: boolean;
  id?: string;
  /** Registers the `mention` node so stored @mentions stay valid while editing (comments); inserted by a picker. */
  mentions?: boolean;
  /** Hands the editor to the parent (e.g. to insert a mention or clear the field); called with null on unmount. */
  onEditor?: (editor: Editor | null) => void;
  minHeight?: number;
}

const TOUCH = 44;

/** The @mention node (id = user id, label = display name). Colours come from the Mantine anchor token. */
const MentionNode = Mention.configure({
  HTMLAttributes: { 'data-mention': 'true', style: 'color: var(--mantine-color-anchor); font-weight: 500;' },
});

/** True when the document has no visible content (only empty paragraphs). */
export function isDocEmpty(doc: RichDoc | null | undefined): boolean {
  if (!doc) return true;
  const walk = (node: unknown): boolean => {
    if (typeof node !== 'object' || node === null) return true;
    const n = node as { type?: string; text?: string; content?: unknown[] };
    if (typeof n.text === 'string' && n.text.trim() !== '') return false;
    if (n.type && n.type !== 'doc' && n.type !== 'paragraph' && n.type !== 'text' && !n.content) return false;
    return (n.content ?? []).every(walk);
  };
  return walk(doc);
}

/**
 * Minimal shared rich-text field (TipTap): bold, italic, bullet and numbered lists. Stores ProseMirror JSON.
 * The full editor (INC-038) will extend or replace this component; the value contract (JSON doc) stays.
 */
export function RichTextField({ label, value, onChange, disabled, id, mentions, onEditor, minHeight = 120 }: Props) {
  const { t } = useTranslation();
  const editor = useEditor({
    extensions: [
      StarterKit.configure({ heading: false, codeBlock: false, blockquote: false, horizontalRule: false }),
      ...(mentions ? [MentionNode] : []),
    ],
    content: value ?? undefined,
    editable: !disabled,
    immediatelyRender: false,
    editorProps: { attributes: { 'aria-label': label, role: 'textbox', 'aria-multiline': 'true', id: id ?? '' } },
    onUpdate: ({ editor: e }) => {
      const doc = e.getJSON() as RichDoc;
      onChange(isDocEmpty(doc) ? undefined : doc);
    },
  });

  useEffect(() => {
    onEditor?.(editor);
    return () => onEditor?.(null);
  }, [editor, onEditor]);

  const tools = editor
    ? [
        { key: 'bold', active: editor.isActive('bold'), run: () => editor.chain().focus().toggleBold().run(), mark: 'B' },
        { key: 'italic', active: editor.isActive('italic'), run: () => editor.chain().focus().toggleItalic().run(), mark: 'I' },
        { key: 'bulletList', active: editor.isActive('bulletList'), run: () => editor.chain().focus().toggleBulletList().run(), mark: '•' },
        { key: 'orderedList', active: editor.isActive('orderedList'), run: () => editor.chain().focus().toggleOrderedList().run(), mark: '1.' },
      ]
    : [];

  return (
    <Input.Wrapper label={label}>
      <Paper withBorder radius="sm" mt={4}>
        <Group gap={4} p={4} style={{ borderBottom: '1px solid var(--mantine-color-default-border)' }}>
          {tools.map((tool) => (
            <ActionIcon
              key={tool.key}
              size={TOUCH}
              variant={tool.active ? 'light' : 'subtle'}
              color={tool.active ? undefined : 'gray'}
              aria-pressed={tool.active}
              aria-label={t(`editor.${tool.key}`)}
              disabled={disabled}
              onClick={tool.run}
            >
              <span aria-hidden>{tool.mark}</span>
            </ActionIcon>
          ))}
        </Group>
        <div style={{ padding: 'var(--mantine-spacing-xs)', minHeight }}>
          <EditorContent editor={editor} />
        </div>
      </Paper>
    </Input.Wrapper>
  );
}

/** Read-only rendering of a stored document. */
export function RichTextView({ value }: { value: RichDoc | null | undefined }) {
  const editor = useEditor({
    extensions: [StarterKit, MentionNode],
    content: value ?? undefined,
    editable: false,
    immediatelyRender: false,
  });
  if (isDocEmpty(value)) return null;
  return <EditorContent editor={editor} />;
}
