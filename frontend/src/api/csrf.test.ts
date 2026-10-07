import createClient from 'openapi-fetch';
import { describe, expect, it } from 'vitest';
import { csrfMiddleware } from './client';
import { isStateChanging, readCookie } from './csrf';

describe('csrf helpers', () => {
  it('reads a cookie by name', () => {
    expect(readCookie('XSRF-TOKEN', 'a=1; XSRF-TOKEN=abc%3D; b=2')).toBe('abc=');
    expect(readCookie('missing', 'a=1')).toBeUndefined();
  });

  it('flags only unsafe methods', () => {
    expect(isStateChanging('GET')).toBe(false);
    expect(isStateChanging('post')).toBe(true);
    expect(isStateChanging('DELETE')).toBe(true);
  });
});

describe('csrf middleware', () => {
  async function send(method: 'GET' | 'POST') {
    let seen: Request | undefined;
    const client = createClient({
      baseUrl: 'http://localhost',
      credentials: 'include',
      fetch: async (request: Request) => {
        seen = request;
        return new Response('{}', { status: 200, headers: { 'content-type': 'application/json' } });
      },
    });
    client.use(csrfMiddleware);
    document.cookie = 'XSRF-TOKEN=token-123; path=/';
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const c = client as any;
    await (method === 'GET' ? c.GET('/x') : c.POST('/x', {}));
    return seen!;
  }

  it('adds X-XSRF-TOKEN to state-changing requests and sends credentials', async () => {
    const request = await send('POST');
    expect(request.headers.get('X-XSRF-TOKEN')).toBe('token-123');
    expect(request.credentials).toBe('include');
  });

  it('does not add the header to safe requests', async () => {
    const request = await send('GET');
    expect(request.headers.get('X-XSRF-TOKEN')).toBeNull();
  });
});
