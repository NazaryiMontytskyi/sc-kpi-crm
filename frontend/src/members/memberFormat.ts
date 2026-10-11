import { facultyName } from './facultyApi';
import type { FacultyRef, MemberSummary } from './memberApi';

const TELEGRAM_USERNAME = /^[A-Za-z0-9_]{5,32}$/;

/** Plain username (no `@`) when `raw` is a valid Telegram username, otherwise null. */
export function telegramUsername(raw: string | null | undefined): string | null {
  const value = (raw ?? '').trim().replace(/^@/, '');
  return TELEGRAM_USERNAME.test(value) ? value : null;
}

/** Safe `https://t.me/<username>` link; null when the stored value is not a valid username. */
export function telegramLink(raw: string | null | undefined): string | null {
  const username = telegramUsername(raw);
  return username ? `https://t.me/${username}` : null;
}

/** "Last First Middle", skipping the empty parts. */
export function fullName(m: Pick<MemberSummary, 'lastName' | 'firstName' | 'middleName'>): string {
  return [m.lastName, m.firstName, m.middleName].filter(Boolean).join(' ');
}

export function initials(m: Pick<MemberSummary, 'lastName' | 'firstName'>): string {
  return `${m.lastName.charAt(0)}${m.firstName.charAt(0)}`.toUpperCase();
}

export function memberFacultyName(faculty: FacultyRef | null, language: string): string {
  return faculty ? facultyName(faculty, language) : '';
}

/** `YYYY-MM-DD` (a calendar date, no time zone) as `DD.MM.YYYY`. */
export function formatDate(isoDate: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  return m ? `${m[3]}.${m[2]}.${m[1]}` : isoDate;
}

/** Case-insensitive text search over name, telegram, group and faculty; blank query matches everything. */
export function matchesSearch(m: MemberSummary, query: string): boolean {
  const q = query.trim().toLowerCase().replace(/^@/, '');
  if (!q) return true;
  const haystack = [
    fullName(m),
    m.telegram,
    m.studyGroup,
    m.faculty?.code,
    m.faculty?.nameUk,
    m.faculty?.nameEn,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  return q.split(/\s+/).every((word) => haystack.includes(word));
}
