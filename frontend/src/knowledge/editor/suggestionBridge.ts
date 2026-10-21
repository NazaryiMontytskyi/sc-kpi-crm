import type { SuggestionKeyDownProps, SuggestionProps } from '@tiptap/suggestion';

export interface MenuState<T> {
  items: T[];
  selected: number;
  clientRect: (() => DOMRect | null) | null | undefined;
  select: (item: T) => void;
}

/**
 * Connects a TipTap suggestion plugin (slash menu, @mentions) to React: the plugin pushes state in through
 * `render()`, a component reads it with `useSyncExternalStore(subscribe, getSnapshot)`. No popper library needed.
 */
export class SuggestionBridge<T> {
  private state: MenuState<T> | null = null;
  private listeners = new Set<() => void>();

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  getSnapshot = () => this.state;

  private set(state: MenuState<T> | null) {
    this.state = state;
    this.listeners.forEach((l) => l());
  }

  /** Called by the menu component when the user taps an item. */
  choose(item: T) {
    this.state?.select(item);
  }

  private fromProps(props: SuggestionProps<T, T>, selected: number): MenuState<T> {
    return { items: props.items, selected, clientRect: props.clientRect, select: (item) => props.command(item) };
  }

  render = () => ({
    onStart: (props: SuggestionProps<T, T>) => this.set(this.fromProps(props, 0)),
    onUpdate: (props: SuggestionProps<T, T>) => {
      const selected = this.state ? Math.min(this.state.selected, Math.max(props.items.length - 1, 0)) : 0;
      this.set(this.fromProps(props, selected));
    },
    onKeyDown: ({ event }: SuggestionKeyDownProps) => {
      const s = this.state;
      if (!s || s.items.length === 0) return false;
      if (event.key === 'ArrowDown') {
        this.set({ ...s, selected: (s.selected + 1) % s.items.length });
        return true;
      }
      if (event.key === 'ArrowUp') {
        this.set({ ...s, selected: (s.selected - 1 + s.items.length) % s.items.length });
        return true;
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        s.select(s.items[s.selected]);
        return true;
      }
      if (event.key === 'Escape') {
        this.set(null);
        return true;
      }
      return false;
    },
    onExit: () => this.set(null),
  });
}
