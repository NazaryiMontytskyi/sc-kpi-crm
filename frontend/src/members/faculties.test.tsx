import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError, fieldErrorsOf } from '../api/problem';
import { STUB_AUTH_STATE, CurrentUserProvider, type AuthState } from '../auth/CurrentUser';
import { RequirePermission } from '../auth/guards';
import { changeLanguage } from '../i18n';
import { visibleNavRoutes } from '../layout/AppLayout';
import { appRoutes } from '../layout/routes';
import { renderWithProviders } from '../test/render';
import { FacultiesPage } from './FacultiesPage';
import { FacultySelect } from './FacultySelect';
import * as api from './facultyApi';
import { validateFaculty } from './facultyValidation';

vi.mock('./facultyApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listFaculties: vi.fn(),
  createFaculty: vi.fn(),
  updateFaculty: vi.fn(),
  archiveFaculty: vi.fn(),
  restoreFaculty: vi.fn(),
}));

const FAKE: api.Faculty[] = [
  { id: 'f1', code: 'FAKE1', nameUk: 'Тестовий факультет', nameEn: 'Test Faculty', archived: false },
  { id: 'f2', code: 'FAKE2', nameUk: 'Старий інститут', nameEn: 'Old Institute', archived: true },
];

const authWith = (permissions: string[]): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id: 'u', displayName: 'Test User', isAdmin: false, permissions },
});

function renderPage(permissions: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={authWith(permissions)}>
        <FacultiesPage />
      </CurrentUserProvider>
    </QueryClientProvider>,
  );
}

// Mantine modals and transitions are slow under jsdom.
vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.listFaculties).mockResolvedValue(FAKE);
});

describe('validateFaculty', () => {
  it('requires code and both names', () => {
    expect(validateFaculty({ code: ' ', nameUk: '', nameEn: '' })).toEqual({
      code: 'faculties.validation.required',
      nameUk: 'faculties.validation.required',
      nameEn: 'faculties.validation.required',
    });
  });
  it('checks the code format and lengths', () => {
    expect(validateFaculty({ code: 'bad code', nameUk: 'a', nameEn: 'b' }).code).toBe('faculties.validation.codeFormat');
    expect(validateFaculty({ code: 'X'.repeat(31), nameUk: 'a', nameEn: 'b' }).code).toBe('faculties.validation.codeTooLong');
    expect(validateFaculty({ code: 'OK-1', nameUk: 'a'.repeat(201), nameEn: 'b' }).nameUk).toBe('faculties.validation.nameTooLong');
    expect(validateFaculty({ code: 'OK-1', nameUk: 'a', nameEn: 'b' })).toEqual({});
  });
});

describe('problem field errors', () => {
  it('parses the errors list and ignores malformed entries', () => {
    expect(fieldErrorsOf({ errors: [{ field: 'nameUk', message: 'm' }, { field: 1 }, null] })).toEqual([
      { field: 'nameUk', message: 'm' },
    ]);
    expect(fieldErrorsOf({})).toEqual([]);
  });
});

describe('permission gating', () => {
  it('hides mutating actions without settings.manage', async () => {
    renderPage([]);
    expect(await screen.findByText('Тестовий факультет')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Додати факультет' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Архівувати' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Редагувати' })).not.toBeInTheDocument();
    expect(api.listFaculties).toHaveBeenCalledWith(false);
  });

  it('shows actions with settings.manage and can include archived', async () => {
    renderPage(['settings.manage']);
    expect(await screen.findByRole('button', { name: 'Додати факультет' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Редагувати' })).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Показати архівні'));
    expect(await screen.findByRole('button', { name: 'Відновити' })).toBeInTheDocument();
    expect(api.listFaculties).toHaveBeenLastCalledWith(true);
  });

  it('hides the nav entry and blocks the route without settings.manage', () => {
    const route = appRoutes.find((r) => r.path === '/settings/faculties')!;
    expect(visibleNavRoutes(appRoutes, authWith([]).user)).not.toContain(route);
    expect(visibleNavRoutes(appRoutes, authWith(['settings.manage']).user)).toContain(route);
    renderWithProviders(
      <CurrentUserProvider value={authWith([])}>
        <RequirePermission permission="settings.manage">
          <p>secret</p>
        </RequirePermission>
      </CurrentUserProvider>,
    );
    expect(screen.queryByText('secret')).not.toBeInTheDocument();
  });
});

describe('create form', () => {
  async function openForm() {
    renderPage(['settings.manage']);
    await userEvent.click(await screen.findByRole('button', { name: 'Додати факультет' }));
    return screen.findByRole('dialog');
  }

  it('validates required fields client-side without calling the API', async () => {
    await openForm();
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findAllByText('Це поле обов’язкове.')).toHaveLength(3);
    expect(api.createFaculty).not.toHaveBeenCalled();
  });

  it('shows server validation errors per field', async () => {
    vi.mocked(api.createFaculty).mockRejectedValue(
      new ApiProblemError(400, 'VALIDATION_FAILED', undefined, [{ field: 'nameEn', message: 'Server says no' }]),
    );
    await openForm();
    await userEvent.type(screen.getByLabelText(/Код/), 'NEW1');
    await userEvent.type(screen.getByLabelText(/українською/), 'Новий');
    await userEvent.type(screen.getByLabelText(/англійською/), 'New');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText('Server says no')).toBeInTheDocument();
    expect(api.createFaculty).toHaveBeenCalledWith({ code: 'NEW1', nameUk: 'Новий', nameEn: 'New' });
  });

  it('attaches a duplicate name to the matching field and a duplicate code to the code', async () => {
    vi.mocked(api.createFaculty).mockRejectedValueOnce(new ApiProblemError(409, 'FACULTY_NAME_TAKEN'));
    await openForm();
    await userEvent.type(screen.getByLabelText(/Код/), 'NEW1');
    await userEvent.type(screen.getByLabelText(/українською/), 'Тестовий факультет');
    await userEvent.type(screen.getByLabelText(/англійською/), 'Brand New');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    const msg = await screen.findAllByText('Активний факультет із такою назвою уже існує.');
    expect(msg).toHaveLength(1);

    vi.mocked(api.createFaculty).mockRejectedValueOnce(new ApiProblemError(409, 'FACULTY_CODE_TAKEN'));
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText('Активний факультет із таким кодом уже існує.')).toBeInTheDocument();
  });

  it('shows a localized message for a backend 403', async () => {
    vi.mocked(api.createFaculty).mockRejectedValue(new ApiProblemError(403, 'FORBIDDEN'));
    await openForm();
    await userEvent.type(screen.getByLabelText(/Код/), 'NEW1');
    await userEvent.type(screen.getByLabelText(/українською/), 'Новий');
    await userEvent.type(screen.getByLabelText(/англійською/), 'New');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText('У вас немає прав на цю дію.')).toBeInTheDocument();
  });
});

describe('archive and restore', () => {
  it('asks for confirmation before archiving', async () => {
    vi.mocked(api.archiveFaculty).mockResolvedValue({ ...FAKE[0]!, archived: true });
    renderPage(['settings.manage']);
    await userEvent.click(await screen.findByRole('button', { name: 'Архівувати' }));
    expect(api.archiveFaculty).not.toHaveBeenCalled();
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('Тестовий факультет');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Архівувати' }));
    await waitFor(() => expect(api.archiveFaculty).toHaveBeenCalledWith('f1'));
  });
});

describe('FacultySelect', () => {
  function Harness() {
    const [value, setValue] = useState<string | null>('f1');
    return <FacultySelect value={value} onChange={setValue} />;
  }
  function renderSelect() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return renderWithProviders(
      <QueryClientProvider client={queryClient}>
        <Harness />
      </QueryClientProvider>,
    );
  }

  it('requests active faculties only and shows names in the UI language', async () => {
    vi.mocked(api.listFaculties).mockResolvedValue([FAKE[0]!]);
    await changeLanguage('en');
    renderSelect();
    expect(await screen.findByDisplayValue('Test Faculty')).toBeInTheDocument();
    expect(api.listFaculties).toHaveBeenCalledWith(false);
  });

  it('uses Ukrainian names by default', async () => {
    vi.mocked(api.listFaculties).mockResolvedValue([FAKE[0]!]);
    renderSelect();
    expect(await screen.findByDisplayValue('Тестовий факультет')).toBeInTheDocument();
  });
});
