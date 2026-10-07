import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { changeLanguage } from './i18n';
import { renderWithProviders } from './test/render';

describe('App placeholder', () => {
  it('renders in uk by default, switches to en, shows product name fallback', async () => {
    await changeLanguage('uk');
    renderWithProviders(<App />);
    expect(screen.getByText('Каркас застосунку готовий')).toBeInTheDocument();
    expect(screen.getByTestId('brand-text')).toHaveTextContent('SC KPI TMS');

    await userEvent.click(screen.getByText('English'));
    expect(await screen.findByText('Application scaffold is ready')).toBeInTheDocument();
    await changeLanguage('uk');
  });
});
