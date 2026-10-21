import { Extension, type Editor, type Extensions, type Range } from '@tiptap/core';
import type { MentionOptions as TipTapMentionOptions } from '@tiptap/extension-mention';
import { TaskItem, TaskList } from '@tiptap/extension-list';
import { TableKit } from '@tiptap/extension-table';
import { TextStyleKit } from '@tiptap/extension-text-style';
import { PluginKey } from '@tiptap/pm/state';
import StarterKit from '@tiptap/starter-kit';
import Suggestion from '@tiptap/suggestion';
import type { BlockCommand } from './blocks';
import type { MentionCandidate } from './mentionCandidates';
import { ArticleImage, Callout, EntityMention, FileAttachment, PdfEmbed } from './nodes';
import type { SuggestionBridge } from './suggestionBridge';

/** Slash menu: typing `/` opens the block list (the same registry as the toolbar Insert menu). */
export interface SlashOptions {
  bridge: SuggestionBridge<BlockCommand>;
  getItems: (query: string) => BlockCommand[];
  run: (item: BlockCommand, editor: Editor) => void;
}

const SlashCommand = Extension.create<SlashOptions>({
  name: 'slashCommand',
  addProseMirrorPlugins() {
    const { bridge, getItems, run } = this.options;
    return [
      Suggestion<BlockCommand, BlockCommand>({
        editor: this.editor,
        char: '/',
        pluginKey: new PluginKey('slashCommand'),
        allowSpaces: false,
        items: ({ query }) => getItems(query),
        command: ({ editor, range, props }: { editor: Editor; range: Range; props: BlockCommand }) => {
          editor.chain().focus().deleteRange(range).run();
          run(props, editor);
        },
        render: bridge.render,
      }),
    ];
  },
});

export interface MentionOptions {
  bridge: SuggestionBridge<MentionCandidate>;
  getItems: (query: string) => Promise<MentionCandidate[]>;
}

export interface ExtensionOptions {
  slash?: SlashOptions;
  /** When set, typing `@` suggests people, articles and tasks. The `mention` node is always registered. */
  mention?: MentionOptions;
}

/**
 * The full article schema. Used by the editor, the read-only view and the tests, so what is edited,
 * stored (TipTap JSON) and displayed always agree.
 */
export function createExtensions(options: ExtensionOptions = {}): Extensions {
  const mention = options.mention;
  return [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      link: {
        openOnClick: false,
        autolink: true,
        HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: '_blank' },
      },
    }),
    TextStyleKit.configure({ fontFamily: false, lineHeight: false }),
    TaskList,
    TaskItem.configure({ nested: true }),
    TableKit.configure({ table: { resizable: true, cellMinWidth: 80 } }),
    ArticleImage.configure({ allowBase64: false }),
    Callout,
    FileAttachment,
    PdfEmbed,
    EntityMention.configure({
      HTMLAttributes: { class: 'sc-mention' },
      ...((mention
        ? {
            suggestion: {
              char: '@',
              pluginKey: new PluginKey('entityMention'),
              items: ({ query }: { query: string }) => mention.getItems(query),
              render: mention.bridge.render,
              command: ({ editor, range, props }: { editor: Editor; range: Range; props: MentionCandidate }) => {
                editor
                  .chain()
                  .focus()
                  .insertContentAt(range, [
                    { type: 'mention', attrs: { id: props.id, label: props.label, entityType: props.entityType } },
                    { type: 'text', text: ' ' },
                  ])
                  .run();
              },
            },
          }
        : { suggestion: { char: '@', pluginKey: new PluginKey('entityMentionIdle'), items: () => [] } }) as Partial<TipTapMentionOptions>),
    }),
    ...(options.slash ? [SlashCommand.configure(options.slash)] : []),
  ];
}
