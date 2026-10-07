import { describe, expect, it } from 'vitest';

// Raw source of every TS/TSX file under src/ (generated client excluded).
const sources = import.meta.glob(['/src/**/*.{ts,tsx}', '!/src/api/generated/**'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

const HEX = /#[0-9a-fA-F]{3,8}\b/;
const COLORS_FILE = '/src/theme/colors.ts';
const TEST_FILE = /\.test\.tsx?$/;

describe('color tokens', () => {
  it('finds source files', () => {
    expect(Object.keys(sources).length).toBeGreaterThan(5);
  });

  it('has hex literals only in theme/colors.ts', () => {
    const offenders = Object.entries(sources)
      .filter(([path]) => path !== COLORS_FILE && !TEST_FILE.test(path))
      .filter(([, code]) => HEX.test(code))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });

  it('keeps the brand color in exactly one non-test place', () => {
    const holders = Object.entries(sources)
      .filter(([path]) => !TEST_FILE.test(path))
      .filter(([, code]) => /#0700CD/i.test(code))
      .map(([path]) => path);
    expect(holders).toEqual([COLORS_FILE]);
  });
});
