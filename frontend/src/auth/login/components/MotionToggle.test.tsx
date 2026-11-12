import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { changeLanguage } from '../../../i18n';
import en from '../../../i18n/locales/en.json';
import uk from '../../../i18n/locales/uk.json';
import { renderWithProviders } from '../../../test/render';
import { resetMotionPauseMemory } from '../hooks/useMotionPreference';
import { loginPageConfig } from '../loginPage.config';
import { stubViewport } from '../testing/viewportStub';
import { LoginShell } from './LoginShell';
import { MotionToggle } from './MotionToggle';

const KEY = loginPageConfig.motion.pauseStorageKey;

describe('MotionToggle (FT-3, WCAG 2.2.2)', () => {
  beforeEach(async () => {
    localStorage.removeItem(KEY);
    resetMotionPauseMemory();
    await changeLanguage('uk');
  });
  afterEach(() => localStorage.removeItem(KEY));

  it('has an i18n name in both languages and reports its state with aria-pressed', async () => {
    renderWithProviders(<MotionToggle />);
    const toggle = screen.getByRole('button', { name: uk.auth.login.motion.pause });
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await act(() => changeLanguage('en'));
    expect(screen.getByRole('button', { name: en.auth.login.motion.pause })).toBe(toggle);
    await act(() => changeLanguage('uk'));
  });

  it('stores the pause on press, reads it back after a remount and resumes on a second press (spec §9.9)', async () => {
    const user = userEvent.setup();
    const first = renderWithProviders(<MotionToggle />);
    await user.click(screen.getByRole('button'));
    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'true');
    expect(localStorage.getItem(KEY)).toBe('true');
    first.unmount();

    renderWithProviders(<MotionToggle />);
    const toggle = screen.getByRole('button');
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    expect(localStorage.getItem(KEY)).toBe('false');
  });

  it('is operable with the keyboard', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MotionToggle />);
    await user.tab();
    expect(screen.getByRole('button')).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'true');
    await user.keyboard(' ');
    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'false');
  });

  it('is the last element in the tab order of the sign-in frame (spec §7)', async () => {
    const viewport = stubViewport(375);
    try {
      const user = userEvent.setup();
      renderWithProviders(
        <LoginShell>
          <input aria-label="login" />
          <button type="submit">submit</button>
        </LoginShell>,
      );
      const toggle = screen.getByRole('button', { name: uk.auth.login.motion.pause });
      screen.getByRole('button', { name: 'submit' }).focus();
      await user.tab();
      expect(toggle).toHaveFocus();
      await user.tab();
      expect(toggle).not.toHaveFocus();
      expect(document.activeElement === document.body || document.activeElement === null).toBe(true);
      // The toggle sits in the footer, after the copyright line.
      expect(screen.getByRole('contentinfo')).toContainElement(toggle);
    } finally {
      viewport.restore();
    }
  });
});
