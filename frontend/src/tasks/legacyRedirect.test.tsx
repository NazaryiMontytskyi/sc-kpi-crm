import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { appRoutes } from '../layout/routes';
import { LegacyTrackerRedirect } from './LegacyTrackerRedirect';
import { legacyTrackerPath } from './trackerNav';

/** The prefix of the retired personal-list URLs (spec §7); only the redirect still knows it. */
const OLD_PREFIX = '/resolutions';

function Where() {
  const loc = useLocation();
  return <output data-testid="where">{loc.pathname + loc.search}</output>;
}

function landOn(url: string): string {
  const route = appRoutes.find((r) => r.path === `${OLD_PREFIX}/*`)!;
  const { unmount } = render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path={route.path} element={route.element} />
        <Route path="/tasks/*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  );
  const where = screen.getByTestId('where').textContent ?? '';
  unmount();
  return where;
}

describe('old personal-list URLs', () => {
  it('map to the tracker', () => {
    expect(legacyTrackerPath('')).toBe('/tasks');
    expect(legacyTrackerPath('mine')).toBe('/tasks');
    expect(legacyTrackerPath('issued')).toBe('/tasks?section=issued');
    expect(legacyTrackerPath('0b6c1f7e-0000-4000-8000-000000000001')).toBe('/tasks/0b6c1f7e-0000-4000-8000-000000000001');
    expect(legacyTrackerPath('a/b')).toBe('/tasks');
  });

  it('are registered as one redirect route without a menu entry', () => {
    const route = appRoutes.find((r) => r.path === `${OLD_PREFIX}/*`);
    expect(route?.element.type).toBe(LegacyTrackerRedirect);
    expect(route?.nav).toBeUndefined();
    expect(appRoutes.filter((r) => r.path.startsWith(OLD_PREFIX))).toHaveLength(1);
  });

  it('redirect to the tracker, dropping the old filters', () => {
    expect(landOn(OLD_PREFIX)).toBe('/tasks');
    expect(landOn(`${OLD_PREFIX}/mine?filter=ACTIVE`)).toBe('/tasks');
    expect(landOn(`${OLD_PREFIX}/issued?filter=SUBMITTED`)).toBe('/tasks?section=issued');
    // Migrated items kept their ids as subtasks (ADR-0010), so an item link opens that task in the panel.
    expect(landOn(`${OLD_PREFIX}/abc-123`)).toBe('/tasks/abc-123');
  });
});
