import { ApiSession } from './api';
import { MEMBER_PASSWORD } from './env';

/** Eight random lowercase letters: unique per call, valid inside logins, names and search queries. */
export function token(): string {
  let out = '';
  for (let i = 0; i < 8; i += 1) out += String.fromCharCode(97 + Math.floor(Math.random() * 26));
  return out;
}

export const capitalize = (s: string): string => s.charAt(0).toUpperCase() + s.slice(1);

/** A fake member with a working session password. */
export interface Actor {
  id: string;
  login: string;
  password: string;
  lastName: string;
  firstName: string;
  /** Display name as shown in pickers and lists: "Last First". */
  fullName: string;
}

interface Page<T> {
  content: T[];
}

export async function firstFacultyId(admin: ApiSession): Promise<string> {
  const faculties = await admin.get<Page<{ id: string }>>('/faculties?page=0&size=50');
  const first = faculties.content[0];
  if (!first) throw new Error('No faculties seeded: the reference data seeder did not run');
  return first.id;
}

export interface RoleRef {
  id: string;
  name: string;
}

export async function createRole(admin: ApiSession, key: string, permissions: string[]): Promise<RoleRef> {
  const name = `E2E ${key} ${token()}`;
  const role = await admin.post<{ id: string }>('/roles', { name, description: 'Fake role created by the e2e suite', permissions });
  return { id: role.id, name };
}

export interface CreatedActor extends Actor {
  /** The one-time password issued at creation (the member must change it at the first sign-in). */
  temporaryPassword: string;
}

/**
 * Creates a fake member through the API. With `activate` (default) the forced password change is performed too,
 * so the actor can sign in and use the app straight away; otherwise the temporary password is the current one.
 */
export async function createActor(
  admin: ApiSession,
  key: string,
  options: { roleId?: string; activate?: boolean } = {},
): Promise<CreatedActor> {
  const t = token();
  const login = `e2e.${key}.${t}`;
  const lastName = `Tester${capitalize(t)}`;
  const firstName = 'Fake';
  const created = await admin.post<{ member: { id: string }; temporaryPassword: string }>('/members', {
    login,
    lastName,
    firstName,
    facultyId: await firstFacultyId(admin),
    studyGroup: 'KP-00',
    telegram: `e2e_${t}`,
    joinedAt: '2024-09-01',
    locale: 'en',
    roleId: options.roleId,
  });
  const temporaryPassword = created.temporaryPassword;
  let password = temporaryPassword;
  if (options.activate ?? true) {
    const own = new ApiSession();
    await own.login(login, temporaryPassword);
    await own.changePassword(temporaryPassword, MEMBER_PASSWORD);
    password = MEMBER_PASSWORD;
  }
  return { id: created.member.id, login, password, lastName, firstName, fullName: `${lastName} ${firstName}`, temporaryPassword };
}

/** Today plus `days`, as YYYY-MM-DD in the Europe/Kyiv calendar (the app validates due dates against it). */
export function kyivDate(days = 0): string {
  const base = new Date(Date.now() + days * 86_400_000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Kyiv' }).format(base);
}
