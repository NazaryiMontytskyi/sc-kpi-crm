import { ActionIcon, Box, Group, Menu, Tooltip } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import type { Editor } from '@tiptap/core';
import { useEditorState } from '@tiptap/react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import {
  BLOCK_COMMANDS,
  COLOR_NAMES,
  TEXT_SIZES,
  backgroundColorValue,
  textColorValue,
  type BlockHost,
  type ColorName,
} from './blocks';

const TOUCH = 44;

interface ToolProps {
  label: string;
  onClick?: () => void;
  active?: boolean;
  disabled?: boolean;
  children: ReactNode;
}

function Tool({ label, onClick, active, disabled, children }: ToolProps) {
  return (
    <Tooltip label={label} withArrow openDelay={400}>
      <ActionIcon
        size={TOUCH}
        variant={active ? 'light' : 'subtle'}
        color={active ? undefined : 'gray'}
        aria-label={label}
        aria-pressed={active === undefined ? undefined : active}
        disabled={disabled}
        // Keep the text selection and focus in the editor.
        onMouseDown={(e) => e.preventDefault()}
        onClick={onClick}
      >
        <span aria-hidden style={{ fontWeight: 600, fontSize: 'var(--mantine-font-size-sm)' }}>
          {children}
        </span>
      </ActionIcon>
    </Tooltip>
  );
}

function swatch(cssVar: string) {
  return (
    <span
      aria-hidden
      style={{ display: 'inline-block', width: 16, height: 16, borderRadius: 4, background: cssVar, border: '1px solid var(--mantine-color-default-border)' }}
    />
  );
}

interface Props {
  editor: Editor;
  host: BlockHost;
  disabled?: boolean;
}

/**
 * Formatting and insert toolbar. On narrow screens it sticks to the bottom (thumb reach, above the keyboard)
 * and scrolls sideways; on wide screens it sticks to the top of the editor and wraps.
 */
export function Toolbar({ editor, host, disabled }: Props) {
  const { t } = useTranslation();
  const mobile = useMediaQuery('(max-width: 48em)') ?? false;
  const s = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      bold: e.isActive('bold'),
      italic: e.isActive('italic'),
      underline: e.isActive('underline'),
      strike: e.isActive('strike'),
      bulletList: e.isActive('bulletList'),
      orderedList: e.isActive('orderedList'),
      taskList: e.isActive('taskList'),
      blockquote: e.isActive('blockquote'),
      link: e.isActive('link'),
      inTable: e.isActive('table'),
      headingLevel: [1, 2, 3].find((level) => e.isActive('heading', { level })) ?? 0,
      canUndo: e.can().undo(),
      canRedo: e.can().redo(),
    }),
  });
  const run = (fn: (chain: ReturnType<Editor['chain']>) => ReturnType<Editor['chain']>) => fn(editor.chain().focus()).run();

  const colorItems = (kind: 'text' | 'background') => (
    <>
      {COLOR_NAMES.map((name: ColorName) => {
        const value = kind === 'text' ? textColorValue(name) : backgroundColorValue(name);
        return (
          <Menu.Item
            key={name}
            mih={TOUCH}
            leftSection={swatch(value)}
            onClick={() => run((c) => (kind === 'text' ? c.setColor(value) : c.setBackgroundColor(value)))}
          >
            {t(`knowledge.colors.${name}`)}
          </Menu.Item>
        );
      })}
      <Menu.Item mih={TOUCH} onClick={() => run((c) => (kind === 'text' ? c.unsetColor() : c.unsetBackgroundColor()))}>
        {t('knowledge.toolbar.clearColor')}
      </Menu.Item>
    </>
  );

  return (
    <Box
      role="toolbar"
      aria-label={t('knowledge.toolbar.label')}
      data-testid="editor-toolbar"
      style={{
        position: 'sticky',
        ...(mobile ? { bottom: 0 } : { top: 'var(--app-shell-header-height, 0px)' }),
        zIndex: 20,
        background: 'var(--mantine-color-body)',
        border: '1px solid var(--mantine-color-default-border)',
        borderRadius: 'var(--mantine-radius-md)',
        padding: 4,
        overflowX: mobile ? 'auto' : 'visible',
      }}
    >
      <Group gap={2} wrap={mobile ? 'nowrap' : 'wrap'} style={{ width: mobile ? 'max-content' : undefined }}>
        <Tool label={t('knowledge.toolbar.undo')} disabled={disabled || !s.canUndo} onClick={() => run((c) => c.undo())}>
          ↶
        </Tool>
        <Tool label={t('knowledge.toolbar.redo')} disabled={disabled || !s.canRedo} onClick={() => run((c) => c.redo())}>
          ↷
        </Tool>

        <Menu withinPortal position="bottom-start">
          <Menu.Target>
            <ActionIcon size={TOUCH} variant="subtle" color="gray" aria-label={t('knowledge.toolbar.heading')} onMouseDown={(e) => e.preventDefault()}>
              <span aria-hidden style={{ fontWeight: 600 }}>
                {s.headingLevel ? `H${s.headingLevel}` : 'H'}
              </span>
            </ActionIcon>
          </Menu.Target>
          <Menu.Dropdown>
            <Menu.Item mih={TOUCH} onClick={() => run((c) => c.setParagraph())}>
              {t('knowledge.toolbar.paragraph')}
            </Menu.Item>
            {[1, 2, 3].map((level) => (
              <Menu.Item key={level} mih={TOUCH} onClick={() => run((c) => c.setHeading({ level: level as 1 | 2 | 3 }))}>
                {t(`knowledge.blocks.heading${level}`)}
              </Menu.Item>
            ))}
          </Menu.Dropdown>
        </Menu>

        <Menu withinPortal position="bottom-start">
          <Menu.Target>
            <ActionIcon size={TOUCH} variant="subtle" color="gray" aria-label={t('knowledge.toolbar.size')} onMouseDown={(e) => e.preventDefault()}>
              <span aria-hidden style={{ fontWeight: 600 }}>
                Aa
              </span>
            </ActionIcon>
          </Menu.Target>
          <Menu.Dropdown>
            {TEXT_SIZES.map((size) => (
              <Menu.Item
                key={size.key}
                mih={TOUCH}
                onClick={() => run((c) => (size.value ? c.setFontSize(size.value) : c.unsetFontSize()))}
              >
                {t(`knowledge.size.${size.key}`)}
              </Menu.Item>
            ))}
          </Menu.Dropdown>
        </Menu>

        <Tool label={t('knowledge.toolbar.bold')} active={s.bold} disabled={disabled} onClick={() => run((c) => c.toggleBold())}>
          B
        </Tool>
        <Tool label={t('knowledge.toolbar.italic')} active={s.italic} disabled={disabled} onClick={() => run((c) => c.toggleItalic())}>
          <em>I</em>
        </Tool>
        <Tool label={t('knowledge.toolbar.underline')} active={s.underline} disabled={disabled} onClick={() => run((c) => c.toggleUnderline())}>
          <u>U</u>
        </Tool>
        <Tool label={t('knowledge.toolbar.strike')} active={s.strike} disabled={disabled} onClick={() => run((c) => c.toggleStrike())}>
          <s>S</s>
        </Tool>

        <Menu withinPortal position="bottom-start">
          <Menu.Target>
            <ActionIcon size={TOUCH} variant="subtle" color="gray" aria-label={t('knowledge.toolbar.textColor')} onMouseDown={(e) => e.preventDefault()}>
              <span aria-hidden style={{ fontWeight: 700, textDecoration: 'underline' }}>
                A
              </span>
            </ActionIcon>
          </Menu.Target>
          <Menu.Dropdown>{colorItems('text')}</Menu.Dropdown>
        </Menu>
        <Menu withinPortal position="bottom-start">
          <Menu.Target>
            <ActionIcon size={TOUCH} variant="subtle" color="gray" aria-label={t('knowledge.toolbar.backgroundColor')} onMouseDown={(e) => e.preventDefault()}>
              <span aria-hidden style={{ fontWeight: 700 }}>
                ▒
              </span>
            </ActionIcon>
          </Menu.Target>
          <Menu.Dropdown>{colorItems('background')}</Menu.Dropdown>
        </Menu>

        <Tool label={t('knowledge.toolbar.bulletList')} active={s.bulletList} disabled={disabled} onClick={() => run((c) => c.toggleBulletList())}>
          •
        </Tool>
        <Tool label={t('knowledge.toolbar.orderedList')} active={s.orderedList} disabled={disabled} onClick={() => run((c) => c.toggleOrderedList())}>
          1.
        </Tool>
        <Tool label={t('knowledge.toolbar.taskList')} active={s.taskList} disabled={disabled} onClick={() => run((c) => c.toggleTaskList())}>
          ☑
        </Tool>
        <Tool label={t('knowledge.toolbar.quote')} active={s.blockquote} disabled={disabled} onClick={() => run((c) => c.toggleBlockquote())}>
          ❝
        </Tool>
        <Tool label={t('knowledge.toolbar.link')} active={s.link} disabled={disabled} onClick={host.askLink}>
          ↗
        </Tool>

        <Menu withinPortal position="bottom-start">
          <Menu.Target>
            <ActionIcon size={TOUCH} variant="light" aria-label={t('knowledge.toolbar.insert')} onMouseDown={(e) => e.preventDefault()}>
              <span aria-hidden style={{ fontWeight: 700 }}>
                +
              </span>
            </ActionIcon>
          </Menu.Target>
          <Menu.Dropdown mah={360} style={{ overflowY: 'auto' }}>
            {BLOCK_COMMANDS.map((b) => (
              <Menu.Item key={b.key} mih={TOUCH} onClick={() => b.run(editor, host)}>
                {t(`knowledge.blocks.${b.key}`)}
              </Menu.Item>
            ))}
          </Menu.Dropdown>
        </Menu>

        {s.inTable && (
          <Menu withinPortal position="bottom-start">
            <Menu.Target>
              <ActionIcon size={TOUCH} variant="light" aria-label={t('knowledge.toolbar.tableTools')} onMouseDown={(e) => e.preventDefault()}>
                <span aria-hidden style={{ fontWeight: 700 }}>
                  ▦
                </span>
              </ActionIcon>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Item mih={TOUCH} onClick={() => run((c) => c.addRowAfter())}>
                {t('knowledge.toolbar.addRow')}
              </Menu.Item>
              <Menu.Item mih={TOUCH} onClick={() => run((c) => c.addColumnAfter())}>
                {t('knowledge.toolbar.addColumn')}
              </Menu.Item>
              <Menu.Item mih={TOUCH} onClick={() => run((c) => c.deleteRow())}>
                {t('knowledge.toolbar.deleteRow')}
              </Menu.Item>
              <Menu.Item mih={TOUCH} onClick={() => run((c) => c.deleteColumn())}>
                {t('knowledge.toolbar.deleteColumn')}
              </Menu.Item>
              <Menu.Item mih={TOUCH} color="red" onClick={() => run((c) => c.deleteTable())}>
                {t('knowledge.toolbar.deleteTable')}
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        )}
      </Group>
    </Box>
  );
}
