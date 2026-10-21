import { Paper, Text, UnstyledButton } from '@mantine/core';
import { useSyncExternalStore, type ReactNode } from 'react';
import type { SuggestionBridge } from './suggestionBridge';

interface Props<T> {
  bridge: SuggestionBridge<T>;
  label: string;
  itemKey: (item: T) => string;
  renderItem: (item: T) => ReactNode;
  empty?: string;
  testId?: string;
}

const MENU_WIDTH = 280;

/** Floating list for a suggestion plugin (slash menu, @mentions); positioned at the caret and kept inside the viewport. */
export function SuggestionMenu<T>({ bridge, label, itemKey, renderItem, empty, testId }: Props<T>) {
  const state = useSyncExternalStore(bridge.subscribe, bridge.getSnapshot);
  if (!state) return null;
  const rect = state.clientRect?.() ?? null;
  const left = rect ? Math.max(8, Math.min(rect.left, window.innerWidth - MENU_WIDTH - 8)) : 8;
  const top = rect ? rect.bottom + 4 : 80;
  return (
    <Paper
      withBorder
      shadow="md"
      radius="md"
      p={4}
      role="listbox"
      aria-label={label}
      data-testid={testId}
      style={{ position: 'fixed', left, top, width: MENU_WIDTH, maxWidth: 'calc(100vw - 16px)', maxHeight: 280, overflowY: 'auto', zIndex: 400 }}
    >
      {state.items.length === 0 && empty && (
        <Text size="sm" c="dimmed" p="xs">
          {empty}
        </Text>
      )}
      {state.items.map((item, index) => (
        <UnstyledButton
          key={itemKey(item)}
          role="option"
          aria-selected={index === state.selected}
          // Keep the editor focused so the typed query is not lost.
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => bridge.choose(item)}
          px="sm"
          style={{
            display: 'flex',
            alignItems: 'center',
            width: '100%',
            minHeight: 44,
            borderRadius: 'var(--mantine-radius-sm)',
            background: index === state.selected ? 'var(--mantine-color-default-hover)' : undefined,
          }}
        >
          {renderItem(item)}
        </UnstyledButton>
      ))}
    </Paper>
  );
}
