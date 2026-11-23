import { useEffect, type KeyboardEvent as ReactKeyboardEvent } from 'react';

/**
 * Keyboard support of the tracker (spec UI-7): Esc closes the side panel, ArrowUp/ArrowDown move between the tasks
 * of the list. Keys typed into fields, editors, pickers or open dialogs are never taken over.
 */

/** Attribute of the card link of a task (TaskCard, board cards); its value is the task id. */
export const TASK_LINK_ATTR = 'data-task-link';

const EDITABLE_ROLES = new Set(['textbox', 'combobox', 'listbox', 'option', 'searchbox', 'spinbutton', 'slider', 'menu', 'menuitem']);

/** True for elements that use the keys themselves: inputs, text areas, selects, rich-text editors, pickers. */
export function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  if (target.isContentEditable || target.closest('[contenteditable="true"], [contenteditable=""]')) return true;
  const role = target.getAttribute('role');
  return role !== null && EDITABLE_ROLES.has(role);
}

/** True when a modal dialog (form, confirmation, drawer) is open: Esc belongs to it. */
export function modalOpen(doc: Document = document): boolean {
  return doc.querySelector('[role="dialog"][aria-modal="true"]') !== null;
}

/** Whether an Escape keydown should close the side panel. */
export function shouldCloseOnEscape(event: Pick<KeyboardEvent, 'key' | 'defaultPrevented' | 'target' | 'isComposing'>, doc: Document = document): boolean {
  if (event.key !== 'Escape' || event.defaultPrevented || event.isComposing) return false;
  if (isEditableTarget(event.target)) return false;
  return !modalOpen(doc);
}

/**
 * The task link to move to from `current` (an element inside the list) with ArrowDown (+1) or ArrowUp (-1); null at
 * either end (no wrap-around) or when `current` is not a task link of the list.
 */
export function adjacentTaskLink(links: readonly HTMLElement[], current: Element | null, step: 1 | -1): HTMLElement | null {
  if (!current) return null;
  const index = links.findIndex((l) => l === current);
  if (index < 0) return null;
  return links[index + step] ?? null;
}

/** Task links of a container in document order. */
export function taskLinksIn(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(`[${TASK_LINK_ATTR}]`));
}

/**
 * keydown handler for a container of task lists: ArrowUp/ArrowDown on a task link focus the previous/next task link
 * of the container (across groups) and report it through `onMove` (e.g. to open it in the side panel).
 */
export function handleListArrows(event: ReactKeyboardEvent<HTMLElement>, onMove?: (taskId: string, link: HTMLElement) => void): void {
  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
  if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey || event.defaultPrevented) return;
  const target = event.target as HTMLElement;
  if (!target.hasAttribute?.(TASK_LINK_ATTR)) return;
  const next = adjacentTaskLink(taskLinksIn(event.currentTarget), target, event.key === 'ArrowDown' ? 1 : -1);
  // At either end the key keeps its default (page scroll) behaviour.
  if (!next) return;
  event.preventDefault();
  next.focus();
  const id = next.getAttribute(TASK_LINK_ATTR);
  if (id && onMove) onMove(id, next);
}

/** Closes the side panel on Escape (spec UI-7) unless the key belongs to a field, a picker or a dialog. */
export function useEscapeToClose(onClose: () => void, enabled = true): void {
  useEffect(() => {
    if (!enabled) return undefined;
    const listener = (event: KeyboardEvent) => {
      if (!shouldCloseOnEscape(event)) return;
      event.preventDefault();
      onClose();
    };
    document.addEventListener('keydown', listener);
    return () => document.removeEventListener('keydown', listener);
  }, [onClose, enabled]);
}
