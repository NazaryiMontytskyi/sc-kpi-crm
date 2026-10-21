import { Typography } from '@mantine/core';
import { EditorContent, useEditor } from '@tiptap/react';
import { useEffect, useMemo, type MouseEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import type { RichDoc } from '../knowledgeApi';
import { createExtensions } from './extensions';
import { mentionPath } from './nodes';
import './editor.css';

/** Read mode: the same schema as the editor, not editable; mentions navigate to the person, page or task. */
export function ArticleView({ content }: { content: RichDoc }) {
  const navigate = useNavigate();
  const extensions = useMemo(() => createExtensions(), []);
  const editor = useEditor({ extensions, content, editable: false, immediatelyRender: false });

  useEffect(() => {
    if (editor && JSON.stringify(editor.getJSON()) !== JSON.stringify(content)) {
      editor.commands.setContent(content, { emitUpdate: false });
    }
  }, [editor, content]);

  const onClick = (e: MouseEvent<HTMLDivElement>) => {
    const target = (e.target as HTMLElement).closest<HTMLElement>('[data-type="mention"]');
    const id = target?.getAttribute('data-id');
    if (target && id) {
      e.preventDefault();
      navigate(mentionPath(target.getAttribute('data-entity-type') ?? 'person', id));
    }
  };

  return (
    // The wrapper only delegates clicks on mention chips; keyboard users reach the same targets via the page links.
    <div onClick={onClick} data-testid="article-view">
      <Typography className="sc-editor sc-editor-readonly">
        <EditorContent editor={editor} />
      </Typography>
    </div>
  );
}
