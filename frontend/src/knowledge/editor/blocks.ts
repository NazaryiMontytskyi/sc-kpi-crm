import type { Editor } from '@tiptap/core';
import type { ArticleAttachment } from '../knowledgeApi';
import { fileUrl } from '../../members/memberApi';

export type FileKind = 'image' | 'file' | 'pdf';

/** What the editor component provides to commands that need user interaction (file picker, URL dialog). */
export interface BlockHost {
  pickFile: (kind: FileKind) => void;
  askLink: () => void;
}

export type BlockKey =
  | 'heading1'
  | 'heading2'
  | 'heading3'
  | 'bulletList'
  | 'orderedList'
  | 'taskList'
  | 'quote'
  | 'codeBlock'
  | 'divider'
  | 'callout'
  | 'table'
  | 'link'
  | 'image'
  | 'file'
  | 'pdf'
  | 'mention';

export interface BlockCommand {
  key: BlockKey;
  /** i18n key suffix under `knowledge.blocks.<key>` (label) and `knowledge.blocks.<key>Hint`. */
  keywords: string[];
  run: (editor: Editor, host: BlockHost) => void;
}

/**
 * Every insertable block. The toolbar "Insert" menu and the slash menu both read this one list,
 * so a block available in one is available in the other.
 */
export const BLOCK_COMMANDS: readonly BlockCommand[] = [
  { key: 'heading1', keywords: ['h1', 'heading', 'title'], run: (e) => e.chain().focus().setHeading({ level: 1 }).run() },
  { key: 'heading2', keywords: ['h2', 'heading'], run: (e) => e.chain().focus().setHeading({ level: 2 }).run() },
  { key: 'heading3', keywords: ['h3', 'heading'], run: (e) => e.chain().focus().setHeading({ level: 3 }).run() },
  { key: 'bulletList', keywords: ['ul', 'list', 'bullet'], run: (e) => e.chain().focus().toggleBulletList().run() },
  { key: 'orderedList', keywords: ['ol', 'list', 'number'], run: (e) => e.chain().focus().toggleOrderedList().run() },
  { key: 'taskList', keywords: ['todo', 'check', 'checklist', 'task'], run: (e) => e.chain().focus().toggleTaskList().run() },
  { key: 'quote', keywords: ['blockquote', 'quote'], run: (e) => e.chain().focus().toggleBlockquote().run() },
  { key: 'codeBlock', keywords: ['code', 'pre'], run: (e) => e.chain().focus().toggleCodeBlock().run() },
  { key: 'divider', keywords: ['hr', 'rule', 'line', 'divider'], run: (e) => e.chain().focus().setHorizontalRule().run() },
  { key: 'callout', keywords: ['note', 'info', 'callout', 'alert'], run: (e) => e.chain().focus().toggleCallout().run() },
  { key: 'table', keywords: ['grid', 'table'], run: (e) => e.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run() },
  { key: 'link', keywords: ['url', 'href', 'link'], run: (_e, host) => host.askLink() },
  { key: 'image', keywords: ['picture', 'photo', 'img', 'image'], run: (_e, host) => host.pickFile('image') },
  { key: 'file', keywords: ['attachment', 'upload', 'file', 'document'], run: (_e, host) => host.pickFile('file') },
  { key: 'pdf', keywords: ['pdf', 'embed', 'document'], run: (_e, host) => host.pickFile('pdf') },
  { key: 'mention', keywords: ['mention', 'person', 'article', 'task', 'at'], run: (e) => e.chain().focus().insertContent('@').run() },
];

/** Keeps the commands whose translated label or keywords contain the query (case-insensitive). */
export function filterBlocks(query: string, label: (key: BlockKey) => string): BlockCommand[] {
  const q = query.trim().toLowerCase();
  if (q === '') return [...BLOCK_COMMANDS];
  return BLOCK_COMMANDS.filter((b) => label(b.key).toLowerCase().includes(q) || b.keywords.some((k) => k.includes(q)));
}

// ---- insertion helpers (used after an upload finishes) -------------------------------------

export function insertImage(editor: Editor, file: ArticleAttachment): boolean {
  return editor
    .chain()
    .focus()
    .insertContent({ type: 'image', attrs: { fileId: file.id, src: fileUrl(file.id), alt: file.fileName, title: null } })
    .run();
}

export function insertFileAttachment(editor: Editor, file: ArticleAttachment): boolean {
  return editor
    .chain()
    .focus()
    .insertContent({ type: 'fileAttachment', attrs: { fileId: file.id, fileName: file.fileName, mimeType: file.mimeType, size: file.size } })
    .run();
}

export function insertPdf(editor: Editor, file: ArticleAttachment): boolean {
  return editor
    .chain()
    .focus()
    .insertContent({ type: 'pdfEmbed', attrs: { fileId: file.id, fileName: file.fileName, mimeType: file.mimeType, size: file.size } })
    .run();
}

/** Accept filter and client-side check per kind (the backend re-checks the real type). */
export const FILE_KIND_ACCEPT: Record<FileKind, string> = { image: 'image/*', file: '', pdf: 'application/pdf' };

export function fileMatchesKind(kind: FileKind, file: Pick<File, 'type' | 'name'>): boolean {
  if (kind === 'image') return file.type.startsWith('image/');
  if (kind === 'pdf') return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  return true;
}

// ---- links ---------------------------------------------------------------------------------

/**
 * Normalizes what the user typed into a safe link target: http(s), mailto, tel, site-relative paths.
 * A bare domain gets `https://`. Anything else (e.g. `javascript:`) is rejected with `null`.
 */
export function normalizeLinkUrl(raw: string): string | null {
  const value = raw.trim();
  if (value === '' || /\s/.test(value)) return null;
  if (/^(https?:|mailto:|tel:)/i.test(value)) return value;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  if (/^[a-z][a-z0-9+.-]*:/i.test(value)) return null;
  return /^[^./]+(\.[^./]+)+(\/.*)?$/.test(value) ? `https://${value}` : null;
}

/** Links the selection, or inserts the URL as linked text when nothing is selected. */
export function applyLink(editor: Editor, url: string): boolean {
  const href = normalizeLinkUrl(url);
  if (!href) return false;
  if (editor.state.selection.empty) {
    return editor
      .chain()
      .focus()
      .insertContent({ type: 'text', text: url.trim(), marks: [{ type: 'link', attrs: { href } }] })
      .run();
  }
  return editor.chain().focus().extendMarkRange('link').setLink({ href }).run();
}

// ---- colors and sizes (theme tokens) --------------------------------------------------------

export const COLOR_NAMES = ['red', 'orange', 'yellow', 'green', 'teal', 'blue', 'violet', 'gray'] as const;
export type ColorName = (typeof COLOR_NAMES)[number];

/** Stored value of a text color: a CSS variable defined per theme in `editor.css` (no raw colors in content). */
export const textColorValue = (name: ColorName) => `var(--sc-text-${name})`;
export const backgroundColorValue = (name: ColorName) => `var(--sc-bg-${name})`;

export const TEXT_SIZES = [
  { key: 'small', value: '0.875rem' },
  { key: 'normal', value: null },
  { key: 'large', value: '1.25rem' },
  { key: 'huge', value: '1.5rem' },
] as const;

/** Attachment ids (uploaded files) referenced from a document, in order, without duplicates. */
export function collectFileIds(doc: unknown): string[] {
  const ids: string[] = [];
  const walk = (node: unknown): void => {
    if (typeof node !== 'object' || node === null) return;
    const n = node as { type?: string; attrs?: { fileId?: unknown }; content?: unknown[] };
    if ((n.type === 'image' || n.type === 'fileAttachment' || n.type === 'pdfEmbed') && typeof n.attrs?.fileId === 'string') {
      if (!ids.includes(n.attrs.fileId)) ids.push(n.attrs.fileId);
    }
    (n.content ?? []).forEach(walk);
  };
  walk(doc);
  return ids;
}
