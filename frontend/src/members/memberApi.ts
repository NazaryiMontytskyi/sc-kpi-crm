import { api, type components } from '../api/client';
import { env } from '../config/env';
import { toProblemError } from '../api/problem';

type MemberResponse = components['schemas']['MemberResponse'];
type MemberSummaryResponse = components['schemas']['MemberSummary'];
type FacultyRefResponse = components['schemas']['FacultyRef'];

export type CreateMemberRequest = components['schemas']['CreateMemberRequest'];
export type MemberRequest = components['schemas']['MemberRequest'];
export type SelfEditRequest = components['schemas']['SelfEditRequest'];

export interface FacultyRef {
  id: string;
  code: string;
  nameUk: string;
  nameEn: string;
  active: boolean;
}

/** Directory row (the generated types mark every field optional). */
export interface MemberSummary {
  id: string;
  lastName: string;
  firstName: string;
  middleName: string;
  faculty: FacultyRef | null;
  studyGroup: string;
  telegram: string;
  joinedAt: string;
  avatarFileId: string | null;
  archived: boolean;
}

export interface Member extends MemberSummary {
  login: string;
  bio: string;
  archivedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

/** Backend maximum page size. */
const PAGE_SIZE = 100;
/** Safety cap so a misbehaving server cannot make the client loop forever. */
const MAX_PAGES = 50;

function toFacultyRef(f: FacultyRefResponse | undefined): FacultyRef | null {
  if (!f?.id) return null;
  return { id: f.id, code: f.code ?? '', nameUk: f.nameUk ?? '', nameEn: f.nameEn ?? '', active: f.active !== false };
}

function toSummary(m: MemberSummaryResponse): MemberSummary {
  return {
    id: m.id ?? '',
    lastName: m.lastName ?? '',
    firstName: m.firstName ?? '',
    middleName: m.middleName ?? '',
    faculty: toFacultyRef(m.faculty),
    studyGroup: m.studyGroup ?? '',
    telegram: m.telegram ?? '',
    joinedAt: m.joinedAt ?? '',
    avatarFileId: m.avatarFileId ?? null,
    archived: m.archived === true,
  };
}

export function toMember(m: MemberResponse): Member {
  return {
    ...toSummary(m),
    login: m.login ?? '',
    bio: m.bio ?? '',
    archivedAt: m.archivedAt ?? null,
    createdAt: m.createdAt ?? null,
    updatedAt: m.updatedAt ?? null,
  };
}

/**
 * Whole directory (all pages). The backend has no text search yet, so the UI filters this list client-side;
 * a council team is small enough for that (CONTEXT.md §5.1).
 */
export async function listMembers(includeArchived: boolean): Promise<MemberSummary[]> {
  const all: MemberSummary[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    const { data, error, response } = await api.GET('/api/v1/members', {
      params: { query: { page: { page, size: PAGE_SIZE }, includeArchived } },
    });
    if (!data) throw toProblemError(response, error);
    all.push(...(data.content ?? []).map(toSummary));
    if (data.last !== false) break;
  }
  return all;
}

export async function getMember(id: string): Promise<Member> {
  const { data, error, response } = await api.GET('/api/v1/members/{id}', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toMember(data);
}

export async function createMember(body: CreateMemberRequest): Promise<{ member: Member; temporaryPassword: string }> {
  const { data, error, response } = await api.POST('/api/v1/members', { body });
  if (!data?.member) throw toProblemError(response, error);
  return { member: toMember(data.member), temporaryPassword: data.temporaryPassword ?? '' };
}

export async function updateMember(id: string, body: MemberRequest): Promise<Member> {
  const { data, error, response } = await api.PUT('/api/v1/members/{id}', { params: { path: { id } }, body });
  if (!data) throw toProblemError(response, error);
  return toMember(data);
}

export async function updateSelf(body: SelfEditRequest): Promise<Member> {
  const { data, error, response } = await api.PUT('/api/v1/members/me', { body });
  if (!data) throw toProblemError(response, error);
  return toMember(data);
}

export async function archiveMember(id: string): Promise<Member> {
  const { data, error, response } = await api.POST('/api/v1/members/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toMember(data);
}

export async function restoreMember(id: string): Promise<Member> {
  const { data, error, response } = await api.POST('/api/v1/members/{id}/restore', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toMember(data);
}

/** Returns the one-time temporary password; callers must not store it anywhere. */
export async function resetMemberPassword(id: string): Promise<string> {
  const { data, error, response } = await api.POST('/api/v1/members/{id}/reset-password', {
    params: { path: { id } },
  });
  if (!data) throw toProblemError(response, error);
  return data.temporaryPassword ?? '';
}

/** Uploads an avatar image through the files module and returns the new file id. */
export async function uploadAvatar(file: File): Promise<string> {
  const { data, error, response } = await api.POST('/api/v1/files', {
    // The generated type models the binary part as a string; the serializer sends the real File.
    body: { file: file as unknown as string },
    bodySerializer: () => {
      const form = new FormData();
      form.append('file', file);
      return form;
    },
  });
  if (!data?.id) throw toProblemError(response, error);
  return data.id;
}

/** Same-origin URL of a stored file; the session cookie authorizes the image request. */
export function fileUrl(fileId: string): string {
  return `${env.apiBaseUrl}/api/v1/files/${encodeURIComponent(fileId)}`;
}
