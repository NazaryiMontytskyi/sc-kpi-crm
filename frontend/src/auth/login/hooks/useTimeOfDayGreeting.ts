import { useState } from 'react';

export type DayPeriod = 'morning' | 'day' | 'evening' | 'night';

/**
 * HERO-3: part of the day by the user's local clock. Ranges are inclusive of whole hours:
 * 05-11 morning, 12-17 day, 18-22 evening, 23-04 night.
 */
export function dayPeriod(date: Date): DayPeriod {
  const hour = date.getHours();
  if (hour >= 5 && hour <= 11) return 'morning';
  if (hour >= 12 && hour <= 17) return 'day';
  if (hour >= 18 && hour <= 22) return 'evening';
  return 'night';
}

/** i18n key of the greeting for the given moment. */
export function greetingKey(date: Date): `auth.login.greeting.${DayPeriod}` {
  return `auth.login.greeting.${dayPeriod(date)}`;
}

/**
 * Greeting key for the moment the page was opened (computed once per mount, so no timer runs). The caller
 * translates it, which keeps it reactive to a language switch. `now` is injectable for tests.
 */
export function useTimeOfDayGreeting(now: () => Date = () => new Date()) {
  const [key] = useState(() => greetingKey(now()));
  return key;
}
