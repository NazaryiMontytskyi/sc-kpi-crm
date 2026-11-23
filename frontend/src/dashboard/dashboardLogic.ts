/**
 * Where each counter of the widget leads: the "My tasks" overview of the tracker, which lists exactly these tasks
 * (the counts equal the "My tasks" quick view) with the overdue group first.
 */
export const WIDGET_LINKS = {
  active: '/tasks',
  overdue: '/tasks',
} as const;

export type WidgetCounter = keyof typeof WIDGET_LINKS;

export const WIDGET_COUNTERS: readonly WidgetCounter[] = ['active', 'overdue'];

/** Only a non-zero overdue counter is highlighted (red); other counters stay neutral. */
export function isCounterHighlighted(counter: WidgetCounter, value: number): boolean {
  return counter === 'overdue' && value > 0;
}

/** Tasks shown per deadline group on the main page; the group counts always cover every task. */
export const WIDGET_TASKS_PER_GROUP = 3;
