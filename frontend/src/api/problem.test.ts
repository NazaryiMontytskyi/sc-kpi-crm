import { describe, expect, it } from 'vitest';
import { ApiProblemError, problemCodeOf, problemMessage, toProblemError } from './problem';

describe('problem helpers', () => {
  it('reads the code from a problem body', () => {
    expect(problemCodeOf({ code: 'LOGIN_LOCKED', status: 429 })).toBe('LOGIN_LOCKED');
    expect(problemCodeOf(undefined)).toBeUndefined();
    expect(problemCodeOf({ code: 5 })).toBeUndefined();
  });
  it('captures Retry-After and maps lockout to whole minutes', () => {
    const res = new Response(null, { status: 429, headers: { 'Retry-After': '601' } });
    const err = toProblemError(res, { code: 'LOGIN_LOCKED' });
    expect(err.retryAfterSeconds).toBe(601);
    expect(problemMessage(err)).toEqual({ key: 'problems.LOGIN_LOCKED', params: { minutes: 11 } });
  });
  it('falls back to a generic message for unknown codes and network errors', () => {
    expect(problemMessage(new ApiProblemError(500, 'SOMETHING')).key).toBe('problems.UNKNOWN');
    expect(problemMessage(new TypeError('network')).key).toBe('problems.UNKNOWN');
  });
});
