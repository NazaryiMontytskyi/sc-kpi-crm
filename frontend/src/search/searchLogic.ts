import type { components } from '../api/client';
import type { SearchGroupKey, SearchResult } from './searchApi';

export type SearchHit = components['schemas']['SearchHit'];

/** Display order of the groups. */
export const GROUP_ORDER: SearchGroupKey[] = ['tasks', 'resolutions', 'articles', 'people', 'orgUnits'];

/** Application route a hit opens. */
export function hitLink(group: SearchGroupKey, hit: SearchHit): string {
  switch (group) {
    case 'tasks':
      return `/tasks/${hit.id}`;
    case 'resolutions':
      return hit.parentId ? `/tasks/${hit.parentId}?tab=resolutions` : `/resolutions/${hit.id}`;
    case 'articles':
      return `/knowledge/${hit.id}`;
    case 'people':
      return `/members/${hit.id}`;
    case 'orgUnits':
      return `/org/${hit.id}`;
  }
}

/** Groups to show: empty groups (including those the caller lacks permission for) are hidden. */
export function visibleGroups(result: SearchResult): { key: SearchGroupKey; total: number; items: SearchHit[] }[] {
  return GROUP_ORDER.flatMap((key) => {
    const g = result[key];
    const items = g?.items ?? [];
    return items.length > 0 ? [{ key, total: g?.total ?? items.length, items }] : [];
  });
}
