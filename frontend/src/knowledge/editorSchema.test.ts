import { Editor } from '@tiptap/core';
import { afterEach, describe, expect, it } from 'vitest';
import {
  BLOCK_COMMANDS,
  applyLink,
  backgroundColorValue,
  collectFileIds,
  fileMatchesKind,
  filterBlocks,
  insertFileAttachment,
  insertImage,
  insertPdf,
  normalizeLinkUrl,
  textColorValue,
  type BlockHost,
  type BlockKey,
  type FileKind,
} from './editor/blocks';
import { createExtensions } from './editor/extensions';
import { buildMentionCandidates, flattenTree } from './editor/mentionCandidates';
import { mentionPath } from './editor/nodes';

// jsdom has no layout; ProseMirror asks ranges for their rectangles when it scrolls to the cursor.
Range.prototype.getClientRects = () => Object.assign([], { item: () => null }) as unknown as DOMRectList;
Range.prototype.getBoundingClientRect = () => new DOMRect(0, 0, 0, 0);

const FILE = { id: '11111111-1111-4111-8111-111111111111', fileName: 'plan.pdf', mimeType: 'application/pdf', size: 2048 };
const IMAGE = { id: '22222222-2222-4222-8222-222222222222', fileName: 'photo.png', mimeType: 'image/png', size: 100 };

const editors: Editor[] = [];
function makeEditor(content?: object): Editor {
  const editor = new Editor({
    element: document.createElement('div'),
    extensions: createExtensions(),
    content: content ?? { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Hello world' }] }] },
  });
  editors.push(editor);
  return editor;
}
afterEach(() => {
  editors.splice(0).forEach((e) => e.destroy());
});

/** Host that completes "pick a file" / "ask a link" immediately, as the editor component does after the dialog. */
function host(editor: Editor): BlockHost {
  const files: Record<FileKind, () => void> = {
    image: () => insertImage(editor, IMAGE),
    file: () => insertFileAttachment(editor, FILE),
    pdf: () => insertPdf(editor, FILE),
  };
  return { pickFile: (kind) => files[kind](), askLink: () => applyLink(editor, 'https://example.org/page') };
}

/** The document as it is stored: JSON text and back. */
const stored = (editor: Editor) => JSON.parse(JSON.stringify(editor.getJSON())) as Record<string, unknown>;

function types(node: unknown, out = new Set<string>()): Set<string> {
  if (typeof node === 'object' && node !== null) {
    const n = node as { type?: string; marks?: { type: string }[]; content?: unknown[] };
    if (n.type) out.add(n.type);
    n.marks?.forEach((m) => out.add(`mark:${m.type}`));
    n.content?.forEach((c) => types(c, out));
  }
  return out;
}

function roundTrips(editor: Editor) {
  const json = stored(editor);
  const reloaded = makeEditor(json);
  expect(stored(reloaded)).toEqual(json);
  return json;
}

const NODE_FOR_BLOCK: Record<BlockKey, string> = {
  heading1: 'heading',
  heading2: 'heading',
  heading3: 'heading',
  bulletList: 'bulletList',
  orderedList: 'orderedList',
  taskList: 'taskList',
  quote: 'blockquote',
  codeBlock: 'codeBlock',
  divider: 'horizontalRule',
  callout: 'callout',
  table: 'table',
  link: 'mark:link',
  image: 'image',
  file: 'fileAttachment',
  pdf: 'pdfEmbed',
  mention: 'text',
};

describe('block commands (toolbar Insert menu and slash menu share this registry)', () => {
  it('covers every block required by the knowledge base', () => {
    expect(BLOCK_COMMANDS.map((b) => b.key).sort()).toEqual(Object.keys(NODE_FOR_BLOCK).sort());
  });

  for (const command of BLOCK_COMMANDS.filter((b) => b.key !== 'mention')) {
    it(`inserts "${command.key}" and round-trips through save/load`, () => {
      const editor = makeEditor();
      editor.commands.focus('end');
      command.run(editor, host(editor));
      const json = roundTrips(editor);
      expect(types(json).has(NODE_FOR_BLOCK[command.key])).toBe(true);
    });
  }

  it('the mention block types the @ trigger character', () => {
    const editor = makeEditor();
    editor.commands.focus('end');
    BLOCK_COMMANDS.find((b) => b.key === 'mention')!.run(editor, host(editor));
    expect(editor.getText()).toContain('@');
  });

  it('keeps heading levels and callout variants', () => {
    const editor = makeEditor();
    BLOCK_COMMANDS.find((b) => b.key === 'heading2')!.run(editor, host(editor));
    const json = roundTrips(editor) as { content: { type: string; attrs?: Record<string, unknown> }[] };
    expect(json.content[0]).toMatchObject({ type: 'heading', attrs: { level: 2 } });

    const callout = makeEditor();
    callout.commands.toggleCallout('warning');
    const cj = roundTrips(callout) as { content: { type: string; attrs?: Record<string, unknown> }[] };
    expect(cj.content[0]).toMatchObject({ type: 'callout', attrs: { variant: 'warning' } });
  });

  it('stores uploaded files by id, with name, type and size', () => {
    const editor = makeEditor();
    insertImage(editor, IMAGE);
    insertFileAttachment(editor, FILE);
    insertPdf(editor, FILE);
    const json = roundTrips(editor) as { content: { type: string; attrs: Record<string, unknown> }[] };
    expect(json.content.find((n) => n.type === 'image')?.attrs).toMatchObject({ fileId: IMAGE.id });
    expect(json.content.find((n) => n.type === 'fileAttachment')?.attrs).toMatchObject({
      fileId: FILE.id,
      fileName: 'plan.pdf',
      mimeType: 'application/pdf',
      size: 2048,
    });
    expect(json.content.find((n) => n.type === 'pdfEmbed')?.attrs).toMatchObject({ fileId: FILE.id });
    expect(collectFileIds(json)).toEqual([IMAGE.id, FILE.id]);
  });

  it('renders image and file nodes with a URL derived from the file id', () => {
    const editor = makeEditor();
    insertImage(editor, IMAGE);
    insertFileAttachment(editor, FILE);
    insertPdf(editor, FILE);
    const html = editor.getHTML();
    expect(html).toContain(`/api/v1/files/${IMAGE.id}`);
    expect(html).toContain('data-file-attachment');
    expect(html).toContain('data-pdf-embed');
    expect(html).toContain('type="application/pdf"');
  });
});

describe('formatting round trip', () => {
  it('bold, italic, underline, strike', () => {
    const editor = makeEditor();
    editor.chain().selectAll().toggleBold().toggleItalic().toggleUnderline().toggleStrike().run();
    const json = roundTrips(editor);
    for (const m of ['bold', 'italic', 'underline', 'strike']) expect(types(json).has(`mark:${m}`)).toBe(true);
  });

  it('text size, text color and background color', () => {
    const editor = makeEditor();
    editor
      .chain()
      .selectAll()
      .setFontSize('1.5rem')
      .setColor(textColorValue('red'))
      .setBackgroundColor(backgroundColorValue('yellow'))
      .run();
    const json = roundTrips(editor) as { content: { content: { marks: { type: string; attrs: Record<string, string> }[] }[] }[] };
    const mark = json.content[0].content[0].marks.find((m) => m.type === 'textStyle');
    expect(mark?.attrs).toMatchObject({ fontSize: '1.5rem', color: 'var(--sc-text-red)', backgroundColor: 'var(--sc-bg-yellow)' });
  });

  it('link mark keeps its address', () => {
    const editor = makeEditor();
    editor.commands.selectAll();
    expect(applyLink(editor, 'example.org')).toBe(true);
    const json = roundTrips(editor);
    expect(JSON.stringify(json)).toContain('https://example.org');
  });

  it('mentions keep id, label and entity type for person, page and task', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            { type: 'mention', attrs: { id: 'p1', label: 'Fake Person', entityType: 'person' } },
            { type: 'text', text: ' ' },
            { type: 'mention', attrs: { id: 'a1', label: 'Handbook', entityType: 'article' } },
            { type: 'text', text: ' ' },
            { type: 'mention', attrs: { id: 't1', label: 'Fix site', entityType: 'task' } },
          ],
        },
      ],
    };
    const editor = makeEditor(doc);
    const json = stored(editor) as { content: { content: { type: string; attrs?: { entityType: string } }[] }[] };
    expect(json.content[0].content.filter((n) => n.type === 'mention').map((n) => n.attrs?.entityType)).toEqual(['person', 'article', 'task']);
    roundTrips(editor);
    expect(editor.getHTML()).toContain('data-entity-type="article"');
  });

  it('the stored document is plain TipTap JSON (doc root)', () => {
    const editor = makeEditor();
    expect(stored(editor).type).toBe('doc');
  });
});

describe('helpers', () => {
  it('normalizeLinkUrl accepts safe targets and rejects scripts', () => {
    expect(normalizeLinkUrl('https://a.org/x')).toBe('https://a.org/x');
    expect(normalizeLinkUrl('mailto:a@b.org')).toBe('mailto:a@b.org');
    expect(normalizeLinkUrl('a.org')).toBe('https://a.org');
    expect(normalizeLinkUrl('/knowledge/1')).toBe('/knowledge/1');
    expect(normalizeLinkUrl('javascript:alert(1)')).toBeNull();
    expect(normalizeLinkUrl('//evil.org')).toBeNull();
    expect(normalizeLinkUrl('two words')).toBeNull();
    expect(normalizeLinkUrl('  ')).toBeNull();
    expect(normalizeLinkUrl('plain')).toBeNull();
  });

  it('filterBlocks matches by label and keyword', () => {
    const label = (k: BlockKey) => ({ table: 'Таблиця', checklist: 'x' })[k as 'table'] ?? k;
    expect(filterBlocks('', label)).toHaveLength(BLOCK_COMMANDS.length);
    expect(filterBlocks('табл', label).map((b) => b.key)).toEqual(['table']);
    expect(filterBlocks('todo', label).map((b) => b.key)).toEqual(['taskList']);
    expect(filterBlocks('zzzz', label)).toEqual([]);
  });

  it('fileMatchesKind checks image and PDF uploads', () => {
    expect(fileMatchesKind('image', { type: 'image/png', name: 'a.png' })).toBe(true);
    expect(fileMatchesKind('image', { type: 'application/pdf', name: 'a.pdf' })).toBe(false);
    expect(fileMatchesKind('pdf', { type: '', name: 'A.PDF' })).toBe(true);
    expect(fileMatchesKind('pdf', { type: 'image/png', name: 'a.png' })).toBe(false);
    expect(fileMatchesKind('file', { type: 'text/plain', name: 'a.txt' })).toBe(true);
  });

  it('mention candidates group people, pages and tasks and skip archived people', () => {
    const member = (id: string, lastName: string, archived = false) => ({
      id, lastName, firstName: 'Фейк', middleName: '', faculty: null, studyGroup: '', telegram: '', joinedAt: '2031-01-01', avatarFileId: null, archived,
    });
    const tree = [{ id: 'a1', title: 'Handbook', restricted: false, children: [{ id: 'a2', title: 'Handbook FAQ', restricted: false, children: [] }] }];
    const source = {
      members: [member('m1', 'Handov'), member('m2', 'Handoff', true)],
      articles: flattenTree(tree),
      tasks: [{ id: 't1', title: 'Update handbook' }],
    };
    expect(buildMentionCandidates(source, 'hand').map((c) => `${c.entityType}:${c.id}`)).toEqual(['person:m1', 'article:a1', 'article:a2', 'task:t1']);
    expect(buildMentionCandidates(source, 'zzz')).toEqual([]);
    expect(buildMentionCandidates(source, '', 1).filter((c) => c.entityType === 'article')).toHaveLength(1);
  });

  it('mention paths', () => {
    expect(mentionPath('person', 'x')).toBe('/members/x');
    expect(mentionPath('article', 'x')).toBe('/knowledge/x');
    expect(mentionPath('task', 'x')).toBe('/tasks/x');
  });
});
