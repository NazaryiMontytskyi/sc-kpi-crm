export const CSRF_COOKIE = 'XSRF-TOKEN';
export const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];

export function readCookie(name: string, cookieString: string = document.cookie): string | undefined {
  for (const part of cookieString.split(';')) {
    const [rawKey, ...rest] = part.trim().split('=');
    if (rawKey === name) return decodeURIComponent(rest.join('='));
  }
  return undefined;
}

export function isStateChanging(method: string): boolean {
  return !SAFE_METHODS.includes(method.toUpperCase());
}
