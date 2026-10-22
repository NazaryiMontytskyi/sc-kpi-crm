import { describe, expect, it } from 'vitest';

const sources = import.meta.glob(['/src/**/*.{ts,tsx}', '!/src/api/generated/**'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

const TEST_FILE = /\.test\.tsx?$/;
// Brand shade 6 (#0700CD) has ~1.6:1 contrast on dark surfaces: forbidden as text/icon color anywhere.
const SHADE_6 = /studrada\.6|studrada-6|studrada\[6\]|--mantine-color-studrada-6/;

describe('dark theme contrast', () => {
  it('never uses brand shade 6 directly in components', () => {
    const offenders = Object.entries(sources)
      .filter(([path]) => !TEST_FILE.test(path) && !path.startsWith('/src/theme/'))
      .filter(([, code]) => SHADE_6.test(code))
      .map(([path]) => path);
    expect(offenders).toEqual([]);
  });
});
