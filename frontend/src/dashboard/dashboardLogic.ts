/** Where each counter of the widget leads: the filtered personal list it was computed from. */
export const WIDGET_LINKS = {
  active: '/resolutions/mine?filter=ACTIVE',
  // the overdue ones are the head of the active list (sorted by due date, flagged red)
  overdue: '/resolutions/mine?filter=ACTIVE',
  awaitingAcceptance: '/resolutions/issued?filter=SUBMITTED',
} as const;

export type WidgetCounter = keyof typeof WIDGET_LINKS;

export const WIDGET_COUNTERS: readonly WidgetCounter[] = ['active', 'overdue', 'awaitingAcceptance'];

/** Only a non-zero overdue counter is highlighted (red); other counters stay neutral. */
export function isCounterHighlighted(counter: WidgetCounter, value: number): boolean {
  return counter === 'overdue' && value > 0;
}
