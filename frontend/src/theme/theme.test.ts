import { describe, expect, it } from 'vitest';
import { studrada } from './colors';
import { cssVariablesResolver, DARK_TEXT_SHADE, theme } from './theme';

describe('theme tokens', () => {
  it('uses the studrada palette with brand shade 6', () => {
    expect(studrada).toHaveLength(10);
    expect(studrada[6]).toBe('#0700CD');
    expect(theme.primaryColor).toBe('studrada');
    expect(theme.primaryShade).toEqual({ light: 6, dark: 5 });
    expect(theme.colors?.studrada).toEqual([...studrada]);
  });

  it('never uses shade 6 for links on dark surfaces', () => {
    const resolved = cssVariablesResolver(theme as never);
    expect(DARK_TEXT_SHADE).toBe(2);
    expect(resolved.dark['--mantine-color-anchor']).toBe('var(--mantine-color-studrada-2)');
    expect(resolved.light['--mantine-color-anchor']).toBe('var(--mantine-color-studrada-6)');
  });
});
