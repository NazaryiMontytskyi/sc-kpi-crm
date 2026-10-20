import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';
import type { Appointment, Membership } from '../org/orgApi';

type Schemas = components['schemas'];

/** Backend maximum page size. */
const PAGE_SIZE = 100;
/** Safety cap so a misbehaving server cannot make the client loop forever. */
const MAX_PAGES = 20;

interface PageLike<T> {
  content?: T[];
  last?: boolean;
}

async function fetchAll<T, R>(
  load: (page: number) => Promise<{ data?: PageLike<T>; error?: unknown; response: Response }>,
  map: (item: T) => R,
): Promise<R[]> {
  const all: R[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    const { data, error, response } = await load(page);
    if (!data) throw toProblemError(response, error);
    all.push(...(data.content ?? []).map(map));
    if (data.last !== false) break;
  }
  return all;
}

function toMembership(m: Schemas['MembershipResponse']): Membership {
  return {
    id: m.id ?? '',
    userId: m.userId ?? '',
    orgUnitId: m.orgUnitId ?? '',
    since: m.since ?? '',
    until: m.until ?? null,
    active: m.active === true,
  };
}

function toAppointment(a: Schemas['AppointmentResponse']): Appointment {
  return {
    id: a.id ?? '',
    positionId: a.positionId ?? '',
    userId: a.userId ?? '',
    startDate: a.startDate ?? '',
    endDate: a.endDate ?? null,
    active: a.active === true,
  };
}

/** Unit memberships of one person, current and ended (profile page). */
export function listMembershipsOfUser(userId: string): Promise<Membership[]> {
  return fetchAll(
    (page) =>
      api.GET('/api/v1/org-memberships', {
        params: { query: { userId, page: { page, size: PAGE_SIZE }, includeEnded: true } },
      }),
    toMembership,
  );
}

/** Position appointments of one person, current and ended (profile page). */
export function listAppointmentsOfUser(userId: string): Promise<Appointment[]> {
  return fetchAll(
    (page) =>
      api.GET('/api/v1/appointments', {
        params: { query: { userId, page: { page, size: PAGE_SIZE }, includeEnded: true } },
      }),
    toAppointment,
  );
}
