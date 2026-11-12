import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import en from '../../../i18n/locales/en.json';
import uk from '../../../i18n/locales/uk.json';
import { loginPageConfig } from '../loginPage.config';
import { RotatingTagline } from './RotatingTagline';

const { intervalMs } = loginPageConfig.tagline;
const PHRASES = ['Alpha one', 'Beta two', 'Gamma three'];

/** The phrase exposed to assistive technology (the others are aria-hidden). */
function currentPhrase(): string {
  const tagline = screen.getByTestId('login-tagline');
  const visible = [...tagline.children].filter((el) => !el.hasAttribute('aria-hidden'));
  expect(visible).toHaveLength(1);
  return visible[0].textContent?.trim() ?? '';
}

describe('RotatingTagline (HERO-4)', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('advances every configured interval and wraps around (spec §9.13)', () => {
    render(<RotatingTagline phrases={PHRASES} mode="motion" />);
    expect(currentPhrase()).toBe('Alpha one');
    act(() => {
      vi.advanceTimersByTime(intervalMs - 1);
    });
    expect(currentPhrase()).toBe('Alpha one');
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(currentPhrase()).toBe('Beta two');
    act(() => {
      vi.advanceTimersByTime(intervalMs);
    });
    expect(currentPhrase()).toBe('Gamma three');
    act(() => {
      vi.advanceTimersByTime(intervalMs);
    });
    expect(currentPhrase()).toBe('Alpha one');
  });

  it('leaves no timers after unmount', () => {
    const { unmount } = render(<RotatingTagline phrases={PHRASES} mode="motion" />);
    expect(vi.getTimerCount()).toBe(1);
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('shows a single phrase statically, without any timer', () => {
    render(<RotatingTagline phrases={['Only one']} mode="motion" />);
    expect(vi.getTimerCount()).toBe(0);
    expect(currentPhrase()).toBe('Only one');
    act(() => {
      vi.advanceTimersByTime(intervalMs * 3);
    });
    expect(currentPhrase()).toBe('Only one');
  });

  it('renders nothing without phrases', () => {
    render(<RotatingTagline phrases={[]} mode="motion" />);
    expect(screen.queryByTestId('login-tagline')).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('slides words up and out / in from below with motion (word-level transition)', () => {
    render(<RotatingTagline phrases={PHRASES} mode="motion" />);
    act(() => {
      vi.advanceTimersByTime(intervalMs);
    });
    const [first, second, third] = [...screen.getByTestId('login-tagline').children];
    expect(first).toHaveAttribute('data-state', 'out');
    expect(second).toHaveAttribute('data-state', 'in');
    expect(third).toHaveAttribute('data-state', 'idle');
    expect(second.className).toMatch(/moving/);
    expect(second.querySelectorAll('[class*="word"]')).toHaveLength(2);
  });

  it('only fades under reduced motion: no transform animation class and no word spans (§6.3)', () => {
    render(<RotatingTagline phrases={PHRASES} mode="fade" />);
    act(() => {
      vi.advanceTimersByTime(intervalMs);
    });
    expect(currentPhrase()).toBe('Beta two');
    const tagline = screen.getByTestId('login-tagline');
    expect(tagline.querySelectorAll('[class*="moving"], [class*="word"]')).toHaveLength(0);
  });

  it('stays on the current phrase while paused, with no timer and no motion class', () => {
    const { rerender } = render(<RotatingTagline phrases={PHRASES} mode="motion" />);
    act(() => {
      vi.advanceTimersByTime(intervalMs);
    });
    rerender(<RotatingTagline phrases={PHRASES} mode="static" />);
    expect(vi.getTimerCount()).toBe(0);
    act(() => {
      vi.advanceTimersByTime(intervalMs * 3);
    });
    expect(currentPhrase()).toBe('Beta two');
    const tagline = screen.getByTestId('login-tagline');
    expect(tagline.querySelectorAll('[class*="moving"], [class*="word"]')).toHaveLength(0);
  });

  it('is not a live region, so the rotation is never announced', () => {
    render(<RotatingTagline phrases={PHRASES} mode="motion" />);
    const tagline = screen.getByTestId('login-tagline');
    expect(tagline).not.toHaveAttribute('aria-live');
    expect(tagline).not.toHaveAttribute('role');
  });

  it('switches the phrase with the language, keeping its position', () => {
    const { rerender } = render(<RotatingTagline phrases={uk.auth.login.taglines} mode="motion" />);
    act(() => {
      vi.advanceTimersByTime(intervalMs);
    });
    rerender(<RotatingTagline phrases={en.auth.login.taglines} mode="motion" />);
    expect(currentPhrase()).toBe(en.auth.login.taglines[1]);
  });
});

describe('tagline phrases (user decision, spec §9.11)', () => {
  it('has exactly three phrases, in this order, in both languages', () => {
    expect(uk.auth.login.taglines).toEqual([
      'Зі студентом на одній хвилі',
      'Голос студента КПІ ім. Ігоря Сікорського',
      'Разом робимо КПІ кращим',
    ]);
    expect(en.auth.login.taglines).toEqual([
      'On the same wave with a student',
      'The voice of a student of KPI',
      'Making KPI better together',
    ]);
  });
});
