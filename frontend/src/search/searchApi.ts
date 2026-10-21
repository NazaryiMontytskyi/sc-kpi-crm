import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type Schemas = components['schemas'];

export type SearchGroupKey = 'tasks' | 'resolutions' | 'articles' | 'people' | 'orgUnits';
export type SearchResult = Schemas['SearchResponse'];

/** Minimum query length the backend searches for; shorter queries return empty groups. */
export const MIN_QUERY_LENGTH = 2;
/** Hits per group in the quick-search dialog. */
export const PREVIEW_LIMIT = 5;
/** Hits per group on the results page (backend maximum). */
export const PAGE_LIMIT = 20;

export async function searchAll(q: string, limit: number): Promise<SearchResult> {
  const { data, error, response } = await api.GET('/api/v1/search', { params: { query: { q, limit } } });
  if (!data) throw toProblemError(response, error);
  return data;
}
