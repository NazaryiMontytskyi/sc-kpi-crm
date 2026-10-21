import { mergeAttributes, Node } from '@tiptap/core';
import Image from '@tiptap/extension-image';
import Mention from '@tiptap/extension-mention';
import { fileUrl } from '../../members/memberApi';

export const CALLOUT_VARIANTS = ['info', 'success', 'warning', 'danger'] as const;
export type CalloutVariant = (typeof CALLOUT_VARIANTS)[number];

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    callout: {
      /** Wraps the selected blocks in a callout, or unwraps when already inside one. */
      toggleCallout: (variant?: CalloutVariant) => ReturnType;
    };
  }
}

/** Highlighted block with other blocks inside. Colors come from the `sc-callout` styles (theme tokens). */
export const Callout = Node.create({
  name: 'callout',
  group: 'block',
  content: 'block+',
  defining: true,
  addAttributes() {
    return {
      variant: {
        default: 'info',
        parseHTML: (el) => el.getAttribute('data-callout') ?? 'info',
        renderHTML: (attrs) => ({ 'data-callout': attrs.variant as string }),
      },
    };
  },
  parseHTML() {
    return [{ tag: 'div[data-callout]' }];
  },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes({ class: 'sc-callout' }, HTMLAttributes), 0];
  },
  addCommands() {
    return {
      toggleCallout:
        (variant = 'info') =>
        ({ commands, editor }) =>
          editor.isActive('callout') ? commands.lift('callout') : commands.wrapIn('callout', { variant }),
    };
  },
});

const fileAttrs = () => ({
  fileId: { default: null, parseHTML: (el: HTMLElement) => el.getAttribute('data-file-id'), renderHTML: (a: Record<string, unknown>) => ({ 'data-file-id': a.fileId as string }) },
  fileName: { default: '', parseHTML: (el: HTMLElement) => el.getAttribute('data-file-name') ?? '', renderHTML: (a: Record<string, unknown>) => ({ 'data-file-name': a.fileName as string }) },
  mimeType: { default: '', parseHTML: (el: HTMLElement) => el.getAttribute('data-mime-type') ?? '', renderHTML: (a: Record<string, unknown>) => ({ 'data-mime-type': a.mimeType as string }) },
  size: { default: 0, parseHTML: (el: HTMLElement) => Number(el.getAttribute('data-size') ?? 0), renderHTML: (a: Record<string, unknown>) => ({ 'data-size': String(a.size ?? 0) }) },
});

/** Human-readable file size. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** A downloadable file shown as a card (atom block). The file lives in the files module; only its id is stored. */
export const FileAttachment = Node.create({
  name: 'fileAttachment',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  addAttributes: fileAttrs,
  parseHTML() {
    return [{ tag: 'div[data-file-attachment]' }];
  },
  renderHTML({ node, HTMLAttributes }) {
    const { fileId, fileName, size } = node.attrs as { fileId: string | null; fileName: string; size: number };
    return [
      'div',
      mergeAttributes({ class: 'sc-file', 'data-file-attachment': '' }, HTMLAttributes),
      ['a', { href: fileId ? fileUrl(fileId) : '#', target: '_blank', rel: 'noopener noreferrer', download: fileName }, fileName || fileId || ''],
      ['span', { class: 'sc-file-size' }, formatFileSize(size ?? 0)],
    ];
  },
});

/** A PDF shown inline with a download link as fallback for browsers that cannot display it (atom block). */
export const PdfEmbed = Node.create({
  name: 'pdfEmbed',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  addAttributes: fileAttrs,
  parseHTML() {
    return [{ tag: 'div[data-pdf-embed]' }];
  },
  renderHTML({ node, HTMLAttributes }) {
    const { fileId, fileName } = node.attrs as { fileId: string | null; fileName: string };
    const href = fileId ? fileUrl(fileId) : '#';
    return [
      'div',
      mergeAttributes({ class: 'sc-pdf', 'data-pdf-embed': '' }, HTMLAttributes),
      ['object', { data: href, type: 'application/pdf', title: fileName }, ['a', { href, target: '_blank', rel: 'noopener noreferrer' }, fileName || fileId || '']],
    ];
  },
});

/** Image stored in the files module; `fileId` is the source of truth and the URL is derived from it. */
export const ArticleImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      fileId: {
        default: null,
        parseHTML: (el) => el.getAttribute('data-file-id'),
        renderHTML: (attrs) => (attrs.fileId ? { 'data-file-id': attrs.fileId as string } : {}),
      },
    };
  },
  renderHTML({ node, HTMLAttributes }) {
    const fileId = node.attrs.fileId as string | null;
    return ['img', mergeAttributes(this.options.HTMLAttributes, HTMLAttributes, fileId ? { src: fileUrl(fileId) } : {})];
  },
});

export type MentionEntity = 'person' | 'article' | 'task';

/** @mention of a person, article or task. `attrs.id` is the target id and `attrs.entityType` its kind (backend contract). */
export const EntityMention = Mention.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      entityType: {
        default: 'person',
        parseHTML: (el) => el.getAttribute('data-entity-type') ?? 'person',
        renderHTML: (attrs) => ({ 'data-entity-type': attrs.entityType as string }),
      },
    };
  },
});

/** Route a mention points to. */
export function mentionPath(entityType: string, id: string): string {
  switch (entityType) {
    case 'article':
      return `/knowledge/${id}`;
    case 'task':
      return `/tasks/${id}`;
    default:
      return `/members/${id}`;
  }
}
