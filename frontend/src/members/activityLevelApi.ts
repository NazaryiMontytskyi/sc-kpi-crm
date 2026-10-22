import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type DefinitionResponse = components['schemas']['DefinitionResponse'];
type LevelRefResponse = components['schemas']['LevelRef'];
type ChangeResponse = components['schemas']['ChangeResponse'];

/** Activity level as used by the UI (the generated types mark every field optional). */
export interface ActivityLevel {
  id: string;
  code: string;
  labelUk: string;
  labelEn: string;
  archived: boolean;
}

export interface ActivityLevelChange {
  id: string;
  from: ActivityLevel | null;
  to: ActivityLevel | null;
  comment: string | null;
  changedByLogin: string | null;
  changedAt: string;
}

/** Backend maximum page size; the scale is expected to stay far below it. */
const PAGE_SIZE = 100;

export function toActivityLevel(l: DefinitionResponse | LevelRefResponse | undefined): ActivityLevel | null {
  if (!l?.id) return null;
  return {
    id: l.id,
    code: l.code ?? '',
    labelUk: l.labelUk ?? '',
    labelEn: l.labelEn ?? '',
    archived: l.archived === true,
  };
}

/** Label in the given UI language (falls back to the other language, then the code). */
export function activityLevelLabel(l: Pick<ActivityLevel, 'labelUk' | 'labelEn' | 'code'>, language: string): string {
  const preferred = language.startsWith('en') ? l.labelEn : l.labelUk;
  return preferred || l.labelUk || l.labelEn || l.code;
}

function toChange(c: ChangeResponse): ActivityLevelChange {
  return {
    id: c.id ?? '',
    from: toActivityLevel(c.from),
    to: toActivityLevel(c.to),
    comment: c.comment ?? null,
    changedByLogin: c.changedByLogin ?? null,
    changedAt: c.changedAt ?? '',
  };
}

/** Active definitions of the scale, in the configured order. */
export async function listActivityLevels(): Promise<ActivityLevel[]> {
  const { data, error, response } = await api.GET('/api/v1/activity-levels', {
    params: { query: { size: PAGE_SIZE } },
  });
  if (!data) throw toProblemError(response, error);
  return (data.content ?? []).map((d) => toActivityLevel(d)).filter((l): l is ActivityLevel => l !== null);
}

export async function setActivityLevel(memberId: string, levelId: string, comment: string): Promise<ActivityLevelChange> {
  const { data, error, response } = await api.PUT('/api/v1/members/{id}/activity-level', {
    params: { path: { id: memberId } },
    body: { levelId, comment: comment.trim() || undefined },
  });
  if (!data) throw toProblemError(response, error);
  return toChange(data);
}

export async function listActivityLevelHistory(memberId: string): Promise<ActivityLevelChange[]> {
  const { data, error, response } = await api.GET('/api/v1/members/{id}/activity-level-history', {
    params: { path: { id: memberId }, query: { size: PAGE_SIZE } },
  });
  if (!data) throw toProblemError(response, error);
  return (data.content ?? []).map(toChange);
}
