import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/** INC-055: the login request must never leave without the XSRF-TOKEN cookie, and bootstraps it at most once. */
describe('login CSRF bootstrap', () => {
  const calls: { method: string; path: string; xsrf: string | null }[] = [];

  function clearCookie() {
    document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  }

  beforeEach(() => {
    calls.length = 0;
    clearCookie();
    vi.resetModules();
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request) => {
        const path = new URL(input.url).pathname;
        calls.push({ method: input.method, path, xsrf: input.headers.get('X-XSRF-TOKEN') });
        const json = { 'Content-Type': 'application/json' };
        if (path.endsWith('/auth/me')) {
          document.cookie = 'XSRF-TOKEN=boot-token; path=/';
          return new Response(JSON.stringify({ title: 'Unauthorized' }), { status: 401, headers: json });
        }
        return new Response(JSON.stringify({ id: 'u1', login: 'fake.user', permissions: [] }), {
          status: 200,
          headers: json,
        });
      }),
    );
  });

  afterEach(() => {
    clearCookie();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it('fetches the cookie once, then sends login with X-XSRF-TOKEN, when the cookie is absent', async () => {
    const { loginRequest } = await import('./authApi');
    await loginRequest('fake.user', 'fake-pass-1');
    expect(calls.map((c) => `${c.method} ${c.path}`)).toEqual(['GET /api/v1/auth/me', 'POST /api/v1/auth/login']);
    expect(calls[1].xsrf).toBe('boot-token');
  });

  it('does not bootstrap when the cookie is already present', async () => {
    document.cookie = 'XSRF-TOKEN=existing; path=/';
    const { loginRequest } = await import('./authApi');
    await loginRequest('fake.user', 'fake-pass-1');
    expect(calls.map((c) => `${c.method} ${c.path}`)).toEqual(['POST /api/v1/auth/login']);
    expect(calls[0].xsrf).toBe('existing');
  });
});
