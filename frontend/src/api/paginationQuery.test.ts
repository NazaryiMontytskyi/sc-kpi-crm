import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Regression for INC-054: the OpenAPI contract declares page/size/sort as flat query parameters, so the shared client
 * must emit `?page=0&size=100`, never the deepObject form `page[size]=100` that Spring cannot bind.
 */
describe('pagination query string through the shared client', () => {
  const requested: string[] = [];

  beforeEach(() => {
    requested.length = 0;
    vi.resetModules();
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request | string) => {
        requested.push(typeof input === 'string' ? input : input.url);
        return new Response(JSON.stringify({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 100 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  const queryOf = (url: string) => url.slice(url.indexOf('?'));

  it.each(['/api/v1/members', '/api/v1/faculties', '/api/v1/activity-levels'] as const)(
    'serializes page and size flat for %s',
    async (path) => {
      const { api } = await import('./client');
      await api.GET(path, { params: { query: { page: 0, size: 100 } } });
      expect(requested).toHaveLength(1);
      expect(queryOf(requested[0])).toBe('?page=0&size=100');
      expect(requested[0]).not.toMatch(/\[|%5B/i);
    },
  );

  it('keeps the flat shape in the real API functions', async () => {
    const { listFaculties } = await import('../members/facultyApi');
    const { listActivityLevels } = await import('../members/activityLevelApi');
    const { searchMembers } = await import('../members/memberApi');
    await listFaculties(false);
    await listActivityLevels();
    await searchMembers({ page: 0, sort: 'lastName,asc', q: '' } as Parameters<typeof searchMembers>[0]);
    expect(requested).toHaveLength(3);
    for (const url of requested) {
      expect(url).toMatch(/[?&]size=\d+/);
      expect(url).not.toMatch(/\[|%5B/i);
    }
  });
});
