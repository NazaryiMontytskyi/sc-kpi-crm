import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { dayPeriod, greetingKey, useTimeOfDayGreeting } from './useTimeOfDayGreeting';

/** A local-time date (the greeting follows the user's clock, not UTC). */
const at = (hours: number, minutes: number) => new Date(2026, 10, 12, hours, minutes);

describe('useTimeOfDayGreeting (HERO-3, spec §9.15)', () => {
  it.each([
    [4, 59, 'night'],
    [5, 0, 'morning'],
    [11, 59, 'morning'],
    [12, 0, 'day'],
    [17, 59, 'day'],
    [18, 0, 'evening'],
    [22, 59, 'evening'],
    [23, 0, 'night'],
    [0, 0, 'night'],
  ] as const)('%i:%i is %s', (h, m, period) => {
    expect(dayPeriod(at(h, m))).toBe(period);
    expect(greetingKey(at(h, m))).toBe(`auth.login.greeting.${period}`);
  });

  it('uses local hours, independent of the date', () => {
    expect(dayPeriod(new Date(2027, 0, 1, 23, 30))).toBe('night');
    expect(dayPeriod(new Date(2027, 6, 15, 9, 0))).toBe('morning');
  });

  it('computes the key once per mount from the injected clock', () => {
    let calls = 0;
    const now = () => {
      calls++;
      return at(19, 30);
    };
    const { result, rerender } = renderHook(() => useTimeOfDayGreeting(now));
    expect(result.current).toBe('auth.login.greeting.evening');
    rerender();
    expect(result.current).toBe('auth.login.greeting.evening');
    expect(calls).toBe(1);
  });
});
