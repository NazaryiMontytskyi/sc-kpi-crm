import { act, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { changeLanguage } from '../../../i18n';
import en from '../../../i18n/locales/en.json';
import uk from '../../../i18n/locales/uk.json';
import { renderWithProviders } from '../../../test/render';
import { stubViewport } from '../testing/viewportStub';
import { HERO_DESKTOP_QUERY, HeroPanel } from './HeroPanel';

const WHITE = {
  '../../../assets/brand/logo/logo-full-blue.png': '/full-blue.png',
  '../../../assets/brand/logo/logo-mark-blue.png': '/mark-blue.png',
  '../../../assets/brand/logo/logo-full-white.png': '/full-white.png',
  '../../../assets/brand/logo/logo-mark-white.png': '/mark-white.png',
};
const morning = () => new Date(2026, 10, 12, 8, 15);

describe('HeroPanel', () => {
  let viewport: ReturnType<typeof stubViewport>;
  beforeEach(async () => {
    await changeLanguage('uk');
  });
  afterEach(() => {
    viewport.restore();
    localStorage.removeItem('mantine-color-scheme-value');
  });

  it('uses the 1024 px breakpoint from the config', () => {
    viewport = stubViewport(1024);
    expect(HERO_DESKTOP_QUERY).toBe('(min-width: 64em)');
  });

  it('shows the white full logo from 1024 px, with an accessible name and no chip', () => {
    viewport = stubViewport(1024);
    renderWithProviders(<HeroPanel now={morning} assets={WHITE} />);
    const logo = screen.getByRole('img', { name: uk.app.name });
    expect(logo).toHaveAttribute('src', '/full-white.png');
    expect(logo).not.toHaveAttribute('data-on-chip');
    expect(screen.getByTestId('login-hero')).toHaveAttribute('data-layout', 'desktop');
  });

  it.each([1023, 768, 375, 360])('shows the white mark at %i px', (width) => {
    viewport = stubViewport(width);
    renderWithProviders(<HeroPanel now={morning} assets={WHITE} />);
    const logo = screen.getByRole('img', { name: uk.app.shortName });
    expect(logo).toHaveAttribute('src', '/mark-white.png');
    expect(logo).not.toHaveAttribute('data-on-chip');
    expect(screen.getByTestId('login-hero')).toHaveAttribute('data-layout', 'compact');
  });

  it('never uses the blue artwork or a chip, also in dark theme', () => {
    viewport = stubViewport(1280);
    localStorage.setItem('mantine-color-scheme-value', 'dark');
    renderWithProviders(<HeroPanel now={morning} assets={WHITE} />);
    const logo = screen.getByRole('img');
    expect(logo.getAttribute('src')).toBe('/full-white.png');
    expect(logo).not.toHaveAttribute('data-on-chip');
  });

  it('switches between the full logo and the mark when the viewport crosses 1024 px', () => {
    viewport = stubViewport(1280);
    renderWithProviders(<HeroPanel now={morning} assets={WHITE} />);
    expect(screen.getByRole('img')).toHaveAttribute('src', '/full-white.png');
    act(() => viewport.resize(375));
    expect(screen.getByRole('img')).toHaveAttribute('src', '/mark-white.png');
  });

  it('shows the product name as text when the white files are missing', () => {
    viewport = stubViewport(1280);
    renderWithProviders(<HeroPanel now={morning} assets={{}} />);
    expect(screen.queryByRole('img')).toBeNull();
    expect(screen.getByTestId('brand-text')).toHaveTextContent(uk.app.name);
  });

  it('renders greeting, title (h1) and subtitle from i18n and re-renders them on a language switch', async () => {
    viewport = stubViewport(1280);
    renderWithProviders(<HeroPanel now={morning} assets={WHITE} />);
    expect(screen.getByText(uk.auth.login.greeting.morning)).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: uk.auth.login.title })).toBeInTheDocument();
    expect(screen.getByText(uk.auth.login.subtitle)).toBeInTheDocument();

    await act(() => changeLanguage('en'));
    expect(screen.getByText(en.auth.login.greeting.morning)).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: en.auth.login.title })).toBeInTheDocument();
    expect(screen.getByText(en.auth.login.subtitle)).toBeInTheDocument();
    await act(() => changeLanguage('uk'));
  });

  it('greets by the injected local time', () => {
    viewport = stubViewport(375);
    renderWithProviders(<HeroPanel now={() => new Date(2026, 10, 12, 23, 0)} assets={WHITE} />);
    expect(screen.getByText(uk.auth.login.greeting.night)).toBeInTheDocument();
  });
});

describe('hero copy (HERO-2)', () => {
  it('has the approved title and subtitle, separate from app.name', () => {
    expect(uk.auth.login.title).toBe('Студентська рада КПІ ім. Ігоря Сікорського');
    expect(en.auth.login.title).toBe('Student Council of Igor Sikorsky Kyiv Polytechnic Institute');
    expect(uk.auth.login.subtitle).toBe('Внутрішня система Студради');
    expect(en.auth.login.subtitle).toBe('Student Council internal system');
    expect(uk.auth.login.title).not.toBe(uk.app.name);
    expect(en.auth.login.title).not.toBe(en.app.name);
  });

  it('has all four greetings in both languages', () => {
    for (const locale of [uk, en]) {
      expect(Object.keys(locale.auth.login.greeting).sort()).toEqual(['day', 'evening', 'morning', 'night']);
    }
  });
});
