import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { STUB_AUTH_STATE, CurrentUserProvider, type AuthState } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import { visibleNavRoutes } from '../layout/AppLayout';
import { appRoutes } from '../layout/routes';
import { renderWithProviders } from '../test/render';
import { AuditPage, toFilters } from './AuditPage';
import * as api from './auditApi';
import { diffStates, formatAuditValue } from './auditDiff';
import { EntityHistory } from './EntityHistory';
import { formatKyivDateTime, kyivDayStartIso } from './kyivTime';

vi.mock('./auditApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listAudit: vi.fn(),
  listEntityHistory: vi.fn(),
}));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const ENTRY_UPDATE: api.AuditEntry = {
  id: 'e2',
  actorId: '11111111-2222-3333-4444-555555555555',
  action: 'UPDATE',
  entityType: 'FakeThing',
  entityId: 'aaaaaaaa-0000-0000-0000-000000000001',
  before: { title: 'Стара назва', same: 1 },
  after: { title: 'Нова назва', same: 1 },
  at: '2031-03-05T10:00:00Z',
  ip: null,
};
const ENTRY_CREATE: api.AuditEntry = {
  ...ENTRY_UPDATE,
  id: 'e1',
  action: 'CREATE',
  actorId: '00000000-0000-0000-0000-000000000000',
  before: null,
  after: { title: 'Стара назва' },
  at: '2031-03-01T10:00:00Z',
};
const page = (entries: api.AuditEntry[]): api.AuditPage => ({
  entries,
  page: 0,
  totalPages: 1,
  totalElements: entries.length,
});

const authWith = (permissions: string[]): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id: 'u', displayName: 'Test User', isAdmin: false, permissions },
});

function renderIn(ui: React.ReactElement, permissions: string[] = []) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={authWith(permissions)}>{ui}</CurrentUserProvider>
    </QueryClientProvider>,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.listAudit).mockResolvedValue(page([ENTRY_UPDATE, ENTRY_CREATE]));
  vi.mocked(api.listEntityHistory).mockResolvedValue(page([ENTRY_UPDATE, ENTRY_CREATE]));
});

describe('diffStates', () => {
  it('lists only changed fields, sorted', () => {
    expect(diffStates({ b: 1, a: 'x', c: true }, { b: 2, a: 'x', c: true })).toEqual([
      { field: 'b', before: 1, after: 2, added: false, removed: false },
    ]);
  });
  it('treats a missing snapshot as empty (create / remove)', () => {
    expect(diffStates(null, { a: 1 })).toEqual([{ field: 'a', before: undefined, after: 1, added: true, removed: false }]);
    expect(diffStates({ a: 1 }, undefined)).toEqual([
      { field: 'a', before: 1, after: undefined, added: false, removed: true },
    ]);
  });
  it('compares nested values structurally', () => {
    expect(diffStates({ a: { x: 1 } }, { a: { x: 1 } })).toEqual([]);
    expect(diffStates({ a: [1] }, { a: [2] })).toHaveLength(1);
  });
  it('formats values for display', () => {
    expect(formatAuditValue(null)).toBeNull();
    expect(formatAuditValue(undefined)).toBeNull();
    expect(formatAuditValue(false)).toBe('false');
    expect(formatAuditValue({ a: 1 })).toBe('{"a":1}');
  });
});

describe('Kyiv time', () => {
  it('displays UTC instants in Europe/Kyiv (winter +2, summer +3)', () => {
    expect(formatKyivDateTime('2031-01-15T10:00:00Z', 'en')).toContain('12:00');
    expect(formatKyivDateTime('2031-07-15T10:00:00Z', 'en')).toContain('13:00');
  });
  it('converts a Kyiv calendar day to its UTC start', () => {
    expect(kyivDayStartIso('2031-01-15')).toBe('2031-01-14T22:00:00.000Z');
    expect(kyivDayStartIso('2031-07-15')).toBe('2031-07-14T21:00:00.000Z');
    expect(kyivDayStartIso('2031-01-15', 1)).toBe('2031-01-15T22:00:00.000Z');
    expect(kyivDayStartIso('bad')).toBeUndefined();
  });
  it('builds filters with an exclusive upper bound after the chosen day', () => {
    const f = toFilters({ actorId: 'a', entityType: '', entityId: '', action: 'UPDATE', from: '2031-01-15', to: '2031-01-15' });
    expect(f.from).toBe('2031-01-14T22:00:00.000Z');
    expect(f.to).toBe('2031-01-15T22:00:00.000Z');
    expect(f.action).toBe('UPDATE');
  });
});

describe('navigation', () => {
  it('lists the audit page only for audit.view holders', () => {
    const withPerm = visibleNavRoutes(appRoutes, authWith(['audit.view']).user);
    const without = visibleNavRoutes(appRoutes, authWith([]).user);
    expect(withPerm.some((r) => r.path === '/audit')).toBe(true);
    expect(without.some((r) => r.path === '/audit')).toBe(false);
  });
});

describe('EntityHistory', () => {
  it('shows actor, action, Kyiv time and the before/after diff of changed fields', async () => {
    renderIn(<EntityHistory entityType="FakeThing" entityId="aaaaaaaa-0000-0000-0000-000000000001" />);
    expect(await screen.findByText('Зміна')).toBeInTheDocument();
    expect(screen.getByText('Створення')).toBeInTheDocument();
    expect(screen.getByText('11111111')).toBeInTheDocument();
    expect(screen.getByText(/Автор: Система/)).toBeInTheDocument();
    expect(screen.getByText(/05\.03\.2031.*12:00/)).toBeInTheDocument();
    expect(screen.getByLabelText('Було')).toHaveTextContent('Стара назва');
    expect(screen.getAllByLabelText('Стало')[0]).toHaveTextContent('Нова назва');
    expect(screen.queryByText('same')).not.toBeInTheDocument();
    expect(api.listEntityHistory).toHaveBeenCalledWith('FakeThing', 'aaaaaaaa-0000-0000-0000-000000000001', 0);
  });

  it('shows an empty state', async () => {
    vi.mocked(api.listEntityHistory).mockResolvedValue(page([]));
    renderIn(<EntityHistory entityType="FakeThing" entityId="x" />);
    expect(await screen.findByText('Історія змін порожня.')).toBeInTheDocument();
  });

  it('renders English text', async () => {
    await changeLanguage('en');
    renderIn(<EntityHistory entityType="FakeThing" entityId="x" />);
    expect(await screen.findByText('Updated')).toBeInTheDocument();
  });
});

describe('AuditPage', () => {
  it('loads the log and applies filters (Kyiv day bounds) from the form', async () => {
    renderIn(<AuditPage />, ['audit.view']);
    expect(await screen.findAllByTestId('audit-entry')).toHaveLength(2);
    expect(api.listAudit).toHaveBeenLastCalledWith({}, 0);

    await userEvent.type(screen.getByLabelText(/Автор зміни/), '11111111-2222-3333-4444-555555555555');
    await userEvent.type(screen.getByLabelText('Тип об’єкта'), 'FakeThing');
    await userEvent.type(screen.getByLabelText('З дати'), '2031-01-15');
    await userEvent.click(screen.getByRole('button', { name: 'Застосувати' }));

    await waitFor(() =>
      expect(api.listAudit).toHaveBeenLastCalledWith(
        expect.objectContaining({
          actorId: '11111111-2222-3333-4444-555555555555',
          entityType: 'FakeThing',
          from: '2031-01-14T22:00:00.000Z',
        }),
        0,
      ),
    );
  });

  it('resets filters', async () => {
    renderIn(<AuditPage />, ['audit.view']);
    await screen.findAllByTestId('audit-entry');
    await userEvent.type(screen.getByLabelText('Тип об’єкта'), 'X');
    await userEvent.click(screen.getByRole('button', { name: 'Застосувати' }));
    await userEvent.click(screen.getByRole('button', { name: 'Скинути' }));
    await waitFor(() => expect(api.listAudit).toHaveBeenLastCalledWith({}, 0));
    expect(screen.getByLabelText('Тип об’єкта')).toHaveValue('');
  });
});
