import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { changeLanguage } from '../../../i18n';
import en from '../../../i18n/locales/en.json';
import uk from '../../../i18n/locales/uk.json';
import { loginPageConfig } from '../loginPage.config';
import { SlideIndicator } from './SlideIndicator';

const { maxSegments } = loginPageConfig.indicator;

function renderIndicator(over: Partial<Parameters<typeof SlideIndicator>[0]> = {}) {
  const onSelect = vi.fn();
  const view = render(
    <SlideIndicator
      count={5}
      active={2}
      progressKey={0}
      durationMs={8000}
      running
      mode="animate"
      onSelect={onSelect}
      {...over}
    />,
  );
  return { ...view, onSelect };
}

describe('SlideIndicator (CAP-3, CAP-4)', () => {
  beforeEach(async () => {
    await changeLanguage('uk');
  });

  it.each([2, 5, 10])('shows %i segments for %i photos', (count) => {
    renderIndicator({ count, active: 0 });
    const group = screen.getByRole('group', { name: uk.auth.login.indicator.label });
    expect(within(group).getAllByRole('button')).toHaveLength(count);
    expect(screen.getByTestId('login-indicator')).toHaveAttribute('data-variant', 'segments');
  });

  it.each([11, 12])('shows one continuous line for %i photos', (count) => {
    expect(count).toBeGreaterThan(maxSegments);
    renderIndicator({ count, active: 3 });
    expect(screen.getByTestId('login-indicator')).toHaveAttribute('data-variant', 'line');
    expect(screen.queryAllByRole('button')).toHaveLength(0);
    expect(screen.getByTestId('login-indicator-fill')).toBeInTheDocument();
  });

  it('renders nothing for fewer than two photos', () => {
    renderIndicator({ count: 1, active: 0 });
    expect(screen.queryByTestId('login-indicator')).toBeNull();
  });

  it('marks the active segment and fills only it; earlier ones are done', () => {
    renderIndicator({ count: 5, active: 2 });
    const buttons = screen.getAllByRole('button');
    expect(buttons.map((b) => b.getAttribute('data-state'))).toEqual(['done', 'done', 'active', 'todo', 'todo']);
    expect(buttons[2]).toHaveAttribute('aria-current', 'true');
    expect(buttons.filter((b) => b.hasAttribute('aria-current'))).toHaveLength(1);
    expect(within(buttons[2]).getByTestId('login-indicator-fill')).toBeInTheDocument();
    expect(screen.getAllByTestId('login-indicator-fill')).toHaveLength(1);
  });

  it('names every segment through i18n, in both languages', async () => {
    renderIndicator({ count: 3, active: 0 });
    expect(screen.getByRole('button', { name: 'Фото 2 з 3' })).toBeInTheDocument();
    await changeLanguage('en');
    expect(await screen.findByRole('button', { name: 'Photo 2 of 3' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: en.auth.login.indicator.label })).toBeInTheDocument();
    await changeLanguage('uk');
  });

  it('selects a photo by click, Enter and Space; segments are focusable buttons', async () => {
    const user = userEvent.setup();
    const { onSelect } = renderIndicator({ count: 4, active: 0 });
    const buttons = screen.getAllByRole('button');
    await user.click(buttons[3]);
    expect(onSelect).toHaveBeenLastCalledWith(3);

    buttons[3].blur();
    await user.tab();
    expect(buttons[0]).toHaveFocus();
    await user.tab();
    expect(buttons[1]).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(onSelect).toHaveBeenLastCalledWith(1);
    await user.tab();
    await user.keyboard(' ');
    expect(onSelect).toHaveBeenLastCalledWith(2);
    for (const button of buttons) expect(button).toHaveAttribute('type', 'button');
  });

  it('fills over the slide lifetime and freezes when not running', () => {
    const { rerender, onSelect } = renderIndicator({ durationMs: 8000 });
    expect(screen.getByTestId('login-indicator').style.getPropertyValue('--login-indicator-duration')).toBe('8000ms');
    const fill = screen.getByTestId('login-indicator-fill');
    expect(fill.className).toMatch(/filling/);
    expect(fill).toHaveAttribute('data-running', 'true');

    rerender(
      <SlideIndicator count={5} active={2} progressKey={0} durationMs={8000} running={false} mode="animate" onSelect={onSelect} />,
    );
    expect(screen.getByTestId('login-indicator-fill')).toBe(fill);
    expect(fill).toHaveAttribute('data-running', 'false');
  });

  it('restarts the fill when the slide restarts its lifetime', () => {
    const { rerender, onSelect } = renderIndicator({ progressKey: 1 });
    const before = screen.getByTestId('login-indicator-fill');
    rerender(
      <SlideIndicator count={5} active={2} progressKey={2} durationMs={8000} running mode="animate" onSelect={onSelect} />,
    );
    expect(screen.getByTestId('login-indicator-fill')).not.toBe(before);
  });

  it('is static under reduced motion', () => {
    renderIndicator({ mode: 'static' });
    const fill = screen.getByTestId('login-indicator-fill');
    expect(fill.className).not.toMatch(/filling/);
    expect(fill.className).toMatch(/full/);
  });
});
