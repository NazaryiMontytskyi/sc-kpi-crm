import { DISPLAY_TIME_ZONE } from '../audit/kyivTime';

/** Today as a Europe/Kyiv calendar date `YYYY-MM-DD` (the reference of the backend LocalDate rules). */
export function todayKyiv(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: DISPLAY_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

export function isRealDate(value: string): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return false;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(Date.UTC(y, mo - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === mo - 1 && date.getUTCDate() === d;
}

/** ISO calendar dates compare correctly as strings. */
export function isFutureDate(value: string, today: string = todayKyiv()): boolean {
  return value > today;
}
