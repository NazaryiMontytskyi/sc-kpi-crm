import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { changeLanguage } from '../../../i18n';
import en from '../../../i18n/locales/en.json';
import uk from '../../../i18n/locales/uk.json';
import { renderWithProviders } from '../../../test/render';
import { stubViewport } from '../testing/viewportStub';
import { LoginShell } from './LoginShell';

function renderShell(now = () => new Date(2031, 0, 5, 14, 0)) {
  return renderWithProviders(
    <LoginShell cardTitle="card title" cardSubtitle="card subtitle" now={now}>
      <input aria-label="field" />
    </LoginShell>,
  );
}

describe('LoginShell', () => {
  let viewport: ReturnType<typeof stubViewport>;
  beforeEach(async () => {
    viewport = stubViewport(1280);
    await changeLanguage('uk');
  });
  afterEach(() => viewport.restore());

  it('computes the footer year at runtime (FT-2)', () => {
    renderShell(() => new Date(2031, 0, 5, 14, 0));
    expect(screen.getByRole('contentinfo')).toHaveTextContent('© 2031 Студентська рада КПІ ім. Ігоря Сікорського');
  });

  it('uses the current year by default', () => {
    renderWithProviders(
      <LoginShell cardTitle="t" cardSubtitle="s">
        <span />
      </LoginShell>,
    );
    expect(screen.getByRole('contentinfo')).toHaveTextContent(`© ${new Date().getFullYear()} `);
  });

  it('puts the language switch first, then the hero, the card and the footer', () => {
    renderShell();
    const shell = screen.getByTestId('login-shell');
    const [header, main, footer] = Array.from(shell.children) as HTMLElement[];
    expect(header.tagName).toBe('HEADER');
    expect(within(header).getByRole('radiogroup', { name: uk.common.language })).toBeInTheDocument();
    expect(main.tagName).toBe('MAIN');
    const [hero, card] = Array.from(main.children) as HTMLElement[];
    expect(hero).toBe(screen.getByTestId('login-hero'));
    expect(within(card).getByRole('heading', { level: 2, name: 'card title' })).toBeInTheDocument();
    expect(within(card).getByLabelText('field')).toBeInTheDocument();
    expect(footer.tagName).toBe('FOOTER');
  });

  it('renders the background before the shell when given', () => {
    renderWithProviders(
      <LoginShell cardTitle="t" cardSubtitle="s" background={<div data-testid="bg" />}>
        <span />
      </LoginShell>,
    );
    const following = screen.getByTestId('bg').compareDocumentPosition(screen.getByTestId('login-shell'));
    expect(following & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('switches hero, greeting and footer to English through the switch, without reload (FT-1, spec §9.11)', async () => {
    renderShell(() => new Date(2031, 0, 5, 9, 0));
    expect(screen.getByRole('heading', { level: 1, name: uk.auth.login.title })).toBeInTheDocument();
    expect(screen.getByText(uk.auth.login.greeting.morning)).toBeInTheDocument();

    await userEvent.click(screen.getByText(uk.common.languageEn));
    expect(await screen.findByRole('heading', { level: 1, name: en.auth.login.title })).toBeInTheDocument();
    expect(screen.getByText(en.auth.login.greeting.morning)).toBeInTheDocument();
    expect(screen.getByText(en.auth.login.subtitle)).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent(
      '© 2031 Student Council of Igor Sikorsky Kyiv Polytechnic Institute',
    );
    expect(localStorage.getItem('crm.language')).toBe('en');
    await act(() => changeLanguage('uk'));
  });
});
