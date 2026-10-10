import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

export type FacultyRequest = components['schemas']['FacultyRequest'];
type FacultyResponse = components['schemas']['FacultyResponse'];

/** Faculty as used by the UI (the generated type marks every field optional). */
export interface Faculty {
  id: string;
  code: string;
  nameUk: string;
  nameEn: string;
  archived: boolean;
}

/** Backend maximum page size; the dictionary is expected to stay far below it. */
const PAGE_SIZE = 100;

function toFaculty(f: FacultyResponse): Faculty {
  return {
    id: f.id ?? '',
    code: f.code ?? '',
    nameUk: f.nameUk ?? '',
    nameEn: f.nameEn ?? '',
    archived: f.archived === true,
  };
}

/** Name in the given UI language (falls back to the other language if empty). */
export function facultyName(f: Pick<Faculty, 'nameUk' | 'nameEn'>, language: string): string {
  const preferred = language.startsWith('en') ? f.nameEn : f.nameUk;
  return preferred || f.nameUk || f.nameEn;
}

export async function listFaculties(includeArchived: boolean): Promise<Faculty[]> {
  const { data, error, response } = await api.GET('/api/v1/faculties', {
    params: { query: { page: { size: PAGE_SIZE }, includeArchived } },
  });
  if (!data) throw toProblemError(response, error);
  return (data.content ?? []).map(toFaculty);
}

export async function createFaculty(body: FacultyRequest): Promise<Faculty> {
  const { data, error, response } = await api.POST('/api/v1/faculties', { body });
  if (!data) throw toProblemError(response, error);
  return toFaculty(data);
}

export async function updateFaculty(id: string, body: FacultyRequest): Promise<Faculty> {
  const { data, error, response } = await api.PUT('/api/v1/faculties/{id}', { params: { path: { id } }, body });
  if (!data) throw toProblemError(response, error);
  return toFaculty(data);
}

export async function archiveFaculty(id: string): Promise<Faculty> {
  const { data, error, response } = await api.POST('/api/v1/faculties/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toFaculty(data);
}

export async function restoreFaculty(id: string): Promise<Faculty> {
  const { data, error, response } = await api.POST('/api/v1/faculties/{id}/restore', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toFaculty(data);
}
