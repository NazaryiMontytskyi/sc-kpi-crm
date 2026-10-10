import { describe, expect, it } from 'vitest';
import { formatPageTitle } from './pageTitle';

describe('formatPageTitle', () => {
  it('formats "<Page> · <suffix>"', () => {
    expect(formatPageTitle('Головна', 'ІС СР КПІ')).toBe('Головна · ІС СР КПІ');
  });
  it('falls back to the suffix alone', () => {
    expect(formatPageTitle(undefined, 'ІС СР КПІ')).toBe('ІС СР КПІ');
  });
});
