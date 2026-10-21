import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type Schemas = components['schemas'];

export const ONBOARDING_MANAGE = 'onboarding.manage';

export interface OnboardingItem {
  articleId: string;
  title: string;
  restricted: boolean;
  /** The caller's own read mark; nobody else's is ever returned. */
  read: boolean;
}

export interface OnboardingList {
  id: string;
  title: string;
  orgUnitId: string | null;
  orgUnitName: string | null;
  items: OnboardingItem[];
  archived: boolean;
}

function toList(l: Schemas['OnboardingListResponse']): OnboardingList {
  return {
    id: l.id ?? '',
    title: l.title ?? '',
    orgUnitId: l.orgUnitId ?? null,
    orgUnitName: l.orgUnitName ?? null,
    archived: l.archived === true,
    items: (l.items ?? []).map((i) => ({
      articleId: i.articleId ?? '',
      title: i.title ?? '',
      restricted: i.restricted === true,
      read: i.read === true,
    })),
  };
}

/** The caller's "For newcomers" page: the general list plus the lists of the caller's departments. */
export async function getMyOnboarding(): Promise<OnboardingList[]> {
  const { data, error, response } = await api.GET('/api/v1/onboarding/lists');
  if (!data) throw toProblemError(response, error);
  return data.map(toList);
}

export async function getManagedOnboarding(includeArchived: boolean): Promise<OnboardingList[]> {
  const { data, error, response } = await api.GET('/api/v1/onboarding/manage/lists', {
    params: { query: { includeArchived: includeArchived || undefined } },
  });
  if (!data) throw toProblemError(response, error);
  return data.map(toList);
}

export async function createOnboardingList(title: string, orgUnitId: string | null, articleIds: string[]): Promise<OnboardingList> {
  const { data, error, response } = await api.POST('/api/v1/onboarding/manage/lists', {
    body: { title, orgUnitId: orgUnitId ?? undefined, articleIds },
  });
  if (!data) throw toProblemError(response, error);
  return toList(data);
}

export async function updateOnboardingList(id: string, title: string, articleIds: string[]): Promise<OnboardingList> {
  const { data, error, response } = await api.PUT('/api/v1/onboarding/manage/lists/{id}', {
    params: { path: { id } },
    body: { title, articleIds },
  });
  if (!data) throw toProblemError(response, error);
  return toList(data);
}

export async function archiveOnboardingList(id: string): Promise<OnboardingList> {
  const { data, error, response } = await api.POST('/api/v1/onboarding/manage/lists/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toList(data);
}

export async function restoreOnboardingList(id: string): Promise<OnboardingList> {
  const { data, error, response } = await api.POST('/api/v1/onboarding/manage/lists/{id}/restore', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toList(data);
}

export async function getReadMark(articleId: string): Promise<boolean> {
  const { data, error, response } = await api.GET('/api/v1/articles/{id}/read-mark', { params: { path: { id: articleId } } });
  if (!data) throw toProblemError(response, error);
  return data.read === true;
}

export async function setReadMark(articleId: string, read: boolean): Promise<boolean> {
  const { data, error, response } = read
    ? await api.PUT('/api/v1/articles/{id}/read-mark', { params: { path: { id: articleId } } })
    : await api.POST('/api/v1/articles/{id}/read-mark/withdraw', { params: { path: { id: articleId } } });
  if (!data) throw toProblemError(response, error);
  return data.read === true;
}

export async function getOnboardingPrompt(): Promise<boolean> {
  const { data, error, response } = await api.GET('/api/v1/onboarding/prompt');
  if (!data) throw toProblemError(response, error);
  return data.pending === true;
}

export async function dismissOnboardingPrompt(): Promise<void> {
  const { data, error, response } = await api.POST('/api/v1/onboarding/prompt/dismiss');
  if (!data) throw toProblemError(response, error);
}
