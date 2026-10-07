import { describe, expect, it } from 'vitest';
import i18n, { changeLanguage, DEFAULT_LANGUAGE } from './index';
import en from './locales/en.json';
import uk from './locales/uk.json';

function flatten(obj: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([key, value]) =>
    typeof value === 'object' && value !== null
      ? flatten(value as Record<string, unknown>, `${prefix}${key}.`)
      : [`${prefix}${key}`],
  );
}

describe('i18n', () => {
  it('uk and en have identical key sets', () => {
    expect(flatten(uk).sort()).toEqual(flatten(en).sort());
  });

  it('defines the product name keys', () => {
    expect(en.app.name).toBe('SC KPI TMS (Team Management System)');
    expect(en.app.shortName).toBe('SC KPI TMS');
    expect(uk.app.shortName).toBe('SC KPI TMS');
  });

  it('defaults to uk and switches to en and back', async () => {
    expect(DEFAULT_LANGUAGE).toBe('uk');
    await changeLanguage('uk');
    expect(i18n.t('common.language')).toBe(uk.common.language);
    await changeLanguage('en');
    expect(i18n.t('common.language')).toBe(en.common.language);
    expect(document.documentElement.lang).toBe('en');
    await changeLanguage('uk');
  });

  it('falls back to uk for an unsupported language', async () => {
    await i18n.changeLanguage('de');
    expect(i18n.t('common.language')).toBe(uk.common.language);
    await changeLanguage('uk');
  });
});
