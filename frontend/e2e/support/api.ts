import { BASE_URL } from './env';

/** Minimal subset of the API responses the suite reads. */
export interface Me {
  id: string;
  login: string;
  mustChangePassword: boolean;
  permissions: string[];
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly path: string,
    readonly body: string,
  ) {
    super(`${status} ${path}: ${body.slice(0, 400)}`);
  }
}

/**
 * A cookie-holding API client for setup work (creating fake users, roles, tasks...), so scenarios can spend their
 * time on the user interface under test. It mirrors the SPA: session cookie plus the XSRF-TOKEN echoed in a header.
 * Cookies are handled by hand because the `Secure` session cookie of the prod profile is served over plain http
 * on localhost (browsers accept that, Node cookie jars do not).
 */
export class ApiSession {
  private readonly cookies = new Map<string, string>();

  constructor(private readonly base: string = BASE_URL) {}

  private store(response: Response): void {
    for (const header of response.headers.getSetCookie()) {
      const pair = header.split(';')[0];
      const eq = pair.indexOf('=');
      const name = pair.slice(0, eq).trim();
      const value = pair.slice(eq + 1).trim();
      if (value === '') this.cookies.delete(name);
      else this.cookies.set(name, value);
    }
  }

  private async send(method: string, path: string, body?: unknown): Promise<Response> {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; '),
    };
    const xsrf = this.cookies.get('XSRF-TOKEN');
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const response = await fetch(`${this.base}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    this.store(response);
    return response;
  }

  /** Performs a call; one retry after refreshing the XSRF token when the first attempt was rejected as CSRF. */
  async call<T = unknown>(method: string, path: string, body?: unknown): Promise<T> {
    if (!this.cookies.has('XSRF-TOKEN')) await this.send('GET', '/api/v1/auth/me');
    let response = await this.send(method, path, body);
    if (response.status === 403 && method !== 'GET') {
      const text = await response.clone().text();
      if (text.includes('CSRF')) {
        await this.send('GET', '/api/v1/auth/me');
        response = await this.send(method, path, body);
      }
    }
    const text = await response.text();
    if (!response.ok) throw new ApiError(response.status, path, text);
    return (text ? JSON.parse(text) : undefined) as T;
  }

  get<T = unknown>(path: string): Promise<T> {
    return this.call<T>('GET', `/api/v1${path}`);
  }

  post<T = unknown>(path: string, body?: unknown): Promise<T> {
    return this.call<T>('POST', `/api/v1${path}`, body ?? {});
  }

  put<T = unknown>(path: string, body?: unknown): Promise<T> {
    return this.call<T>('PUT', `/api/v1${path}`, body);
  }

  login(login: string, password: string): Promise<Me> {
    return this.post<Me>('/auth/login', { login, password });
  }

  changePassword(currentPassword: string, newPassword: string): Promise<Me> {
    return this.post<Me>('/auth/change-password', { currentPassword, newPassword });
  }
}
