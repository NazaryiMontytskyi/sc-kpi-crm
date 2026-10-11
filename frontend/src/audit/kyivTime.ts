export const DISPLAY_TIME_ZONE = 'Europe/Kyiv';

/** Formats an ISO instant (UTC from the API) in Europe/Kyiv, e.g. "05.03.2031, 12:00". */
export function formatKyivDateTime(iso: string | undefined, locale: string): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-GB' : 'uk-UA', {
    timeZone: DISPLAY_TIME_ZONE,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(date);
}

function kyivHour(date: Date): number {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: DISPLAY_TIME_ZONE,
    hour: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  return Number(parts.find((p) => p.type === 'hour')?.value);
}

/** UTC instant (ISO) of 00:00 Europe/Kyiv on a `YYYY-MM-DD` day, plus `addDays` days. */
export function kyivDayStartIso(day: string, addDays = 0): string | undefined {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  if (!m) return undefined;
  const [y, mo, d] = [Number(m[1]), Number(m[2]) - 1, Number(m[3]) + addDays];
  // Kyiv is UTC+2 (winter) or UTC+3 (summer): the right offset is the one that yields hour 0 locally.
  for (const offset of [3, 2]) {
    const candidate = new Date(Date.UTC(y, mo, d, -offset));
    if (kyivHour(candidate) === 0) return candidate.toISOString();
  }
  return new Date(Date.UTC(y, mo, d, -2)).toISOString();
}
