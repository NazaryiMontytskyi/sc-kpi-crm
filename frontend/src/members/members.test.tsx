import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { STUB_AUTH_STATE, CurrentUserProvider, type AuthState } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import { appRoutes } from '../layout/routes';
import { renderWithProviders } from '../test/render';
import * as facultyApi from './facultyApi';
import * as api from './memberApi';
import { formatDate, matchesSearch, telegramLink, telegramUsername } from './memberFormat';
import { memberErrorKey, memberFieldErrors } from './memberProblems';
import { MemberProfilePage } from './MemberProfilePage';
import { MembersPage } from './MembersPage';
import { validateMember, type MemberFormValues } from './memberValidation';

vi.mock('./memberApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listMembers: vi.fn(),
  getMember: vi.fn(),
  createMember: vi.fn(),
  updateMember: vi.fn(),
  updateSelf: vi.fn(),
  archiveMember: vi.fn(),
  restoreMember: vi.fn(),
  resetMemberPassword: vi.fn(),
  uploadAvatar: vi.fn(),
}));
vi.mock('./facultyApi', async (orig) => ({ ...(await orig<typeof facultyApi>()), listFaculties: vi.fn() }));
vi.mock('../audit', () => ({ EntityHistory: () => <div data-testid="history" /> }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const FACULTY: api.FacultyRef = { id: 'f1', code: 'FAKE', nameUk: 'Фейковий факультет', nameEn: 'Fake Faculty', active: true };

const ANNA: api.MemberSummary = {
  id: 'm1',
  lastName: 'Тестенко',
  firstName: 'Анна',
  middleName: 'Фейківна',
  faculty: FACULTY,
  studyGroup: 'ТТ-01',
  telegram: 'fake_anna',
  joinedAt: '2031-03-05',
  avatarFileId: null,
  archived: false,
};
const BOHDAN: api.MemberSummary = { ...ANNA, id: 'm2', lastName: 'Прикладний', firstName: 'Богдан', middleName: '', telegram: '@fake_bohdan', studyGroup: 'ПП-22' };
const ARCHIVED: api.MemberSummary = { ...ANNA, id: 'm3', lastName: 'Архівний', firstName: 'Кузьма', archived: true };

const member = (base: api.MemberSummary, extra: Partial<api.Member> = {}): api.Member => ({
  ...base,
  login: 'fake.login',
  bio: 'Фейкова біографія',
  archivedAt: base.archived ? '2031-06-01T09:00:00Z' : null,
  createdAt: '2031-03-05T09:00:00Z',
  updatedAt: '2031-03-06T09:00:00Z',
  ...extra,
});

const authWith = (permissions: string[], id = 'someone'): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id, displayName: 'Test User', isAdmin: false, permissions },
});

function renderIn(ui: React.ReactElement, permissions: string[], route = '/', userId = 'someone') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={authWith(permissions, userId)}>{ui}</CurrentUserProvider>
    </QueryClientProvider>,
    route,
  );
}

function renderProfile(id: string, permissions: string[], userId = 'someone') {
  return renderIn(
    <Routes>
      <Route path="/members/:id" element={<MemberProfilePage />} />
    </Routes>,
    permissions,
    `/members/${id}`,
    userId,
  );
}

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(api.listMembers).mockImplementation(async (includeArchived) =>
    includeArchived ? [ANNA, BOHDAN, ARCHIVED] : [ANNA, BOHDAN],
  );
  vi.mocked(api.getMember).mockResolvedValue(member(ANNA));
  vi.mocked(facultyApi.listFaculties).mockResolvedValue([
    { id: 'f1', code: 'FAKE', nameUk: 'Фейковий факультет', nameEn: 'Fake Faculty', archived: false },
  ]);
});

describe('telegram helpers', () => {
  it('builds a t.me link only from a valid username', () => {
    expect(telegramLink('fake_anna')).toBe('https://t.me/fake_anna');
    expect(telegramLink(' @fake_anna ')).toBe('https://t.me/fake_anna');
    expect(telegramUsername('@fake_anna')).toBe('fake_anna');
  });
  it('rejects anything that could break out of the link', () => {
    expect(telegramLink('abc/../x')).toBeNull();
    expect(telegramLink('javascript:alert(1)')).toBeNull();
    expect(telegramLink('fake anna')).toBeNull();
    expect(telegramLink('ab')).toBeNull();
    expect(telegramLink('')).toBeNull();
  });
});

describe('formatting and search', () => {
  it('formats calendar dates without time zone shifts', () => {
    expect(formatDate('2031-03-05')).toBe('05.03.2031');
  });
  it('searches name, telegram, group and faculty case-insensitively', () => {
    expect(matchesSearch(ANNA, 'тесте')).toBe(true);
    expect(matchesSearch(ANNA, 'АННА тестенко')).toBe(true);
    expect(matchesSearch(ANNA, '@FAKE_anna')).toBe(true);
    expect(matchesSearch(ANNA, 'тт-01')).toBe(true);
    expect(matchesSearch(ANNA, 'fake faculty')).toBe(true);
    expect(matchesSearch(ANNA, 'богдан')).toBe(false);
    expect(matchesSearch(ANNA, '  ')).toBe(true);
  });
});

describe('validateMember', () => {
  const valid: MemberFormValues = {
    login: 'fake.login',
    lastName: 'Тестенко',
    firstName: 'Анна',
    middleName: '',
    facultyId: 'f1',
    studyGroup: 'ТТ-01',
    telegram: '@fake_anna',
    joinedAt: '2031-03-05',
    bio: '',
  };
  it('accepts a complete form', () => {
    expect(validateMember(valid, { withLogin: true })).toEqual({});
  });
  it('requires the mandatory fields', () => {
    const errors = validateMember(
      { ...valid, login: '', lastName: ' ', firstName: '', facultyId: null, studyGroup: '', telegram: '', joinedAt: '' },
      { withLogin: true },
    );
    expect(Object.keys(errors).sort()).toEqual(
      ['facultyId', 'firstName', 'joinedAt', 'lastName', 'login', 'studyGroup', 'telegram'].sort(),
    );
  });
  it('checks formats and lengths', () => {
    expect(validateMember({ ...valid, login: 'a b' }, { withLogin: true }).login).toBe('members.validation.loginFormat');
    expect(validateMember({ ...valid, telegram: 'abc' }, { withLogin: false }).telegram).toBe('members.validation.telegramFormat');
    expect(validateMember({ ...valid, joinedAt: '2031-02-31' }, { withLogin: false }).joinedAt).toBe('members.validation.dateInvalid');
    expect(validateMember({ ...valid, bio: 'x'.repeat(4001) }, { withLogin: false }).bio).toBe('members.validation.bioTooLong');
  });
  it('validates only telegram and bio for self-edit', () => {
    expect(validateMember({ ...valid, lastName: '', facultyId: null }, { withLogin: false, selfOnly: true })).toEqual({});
  });
});

describe('server problems', () => {
  it('maps validation errors and known codes to fields', () => {
    const validation = new ApiProblemError(400, 'VALIDATION_FAILED', undefined, [{ field: 'lastName', message: 'Порожнє' }]);
    expect(memberFieldErrors(validation)).toEqual({ lastName: { text: 'Порожнє' } });
    expect(memberFieldErrors(new ApiProblemError(409, 'LOGIN_TAKEN'))).toEqual({ login: { key: 'problems.LOGIN_TAKEN' } });
  });
  it('reads any 403 as a missing permission', () => {
    expect(memberErrorKey(new ApiProblemError(403, undefined))).toBe('problems.FORBIDDEN');
    expect(memberErrorKey(new ApiProblemError(409, 'MEMBER_ARCHIVED'))).toBe('problems.MEMBER_ARCHIVED');
    expect(memberErrorKey(new TypeError('network'))).toBe('problems.UNKNOWN');
  });
});

describe('routes', () => {
  it('registers the directory, the profile and /profile (which enables the profile menu item)', () => {
    const paths = appRoutes.map((r) => r.path);
    expect(paths).toEqual(expect.arrayContaining(['/members', '/members/:id', '/profile']));
    expect(appRoutes.find((r) => r.path === '/members')?.nav).toBeDefined();
  });
});

describe('MembersPage', () => {
  it('lists members and filters them by the search text', async () => {
    renderIn(<MembersPage />, []);
    expect(await screen.findByText('Тестенко Анна Фейківна')).toBeInTheDocument();
    expect(screen.getByText('Прикладний Богдан')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Пошук'), 'богдан');
    expect(screen.queryByText('Тестенко Анна Фейківна')).not.toBeInTheDocument();
    expect(screen.getByText('Прикладний Богдан')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Пошук'));
    await userEvent.type(screen.getByLabelText('Пошук'), 'немає такого');
    expect(await screen.findByText('За вашим запитом нікого не знайдено.')).toBeInTheDocument();
  });

  it('links telegram contacts safely', async () => {
    renderIn(<MembersPage />, []);
    const link = await screen.findByRole('link', { name: '@fake_anna' });
    expect(link).toHaveAttribute('href', 'https://t.me/fake_anna');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.getByRole('link', { name: '@fake_bohdan' })).toHaveAttribute('href', 'https://t.me/fake_bohdan');
  });

  it('hides create and the archived switch without permissions', async () => {
    renderIn(<MembersPage />, ['members.view']);
    await screen.findByText('Тестенко Анна Фейківна');
    expect(screen.queryByRole('button', { name: 'Додати учасника' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Показати архівованих')).not.toBeInTheDocument();
  });

  it('shows archived members only to members.archive holders who ask for them', async () => {
    renderIn(<MembersPage />, ['members.archive']);
    await screen.findByText('Тестенко Анна Фейківна');
    expect(screen.queryByText('Архівний Кузьма Фейківна')).not.toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Показати архівованих'));
    expect(await screen.findByText('Архівний Кузьма Фейківна')).toBeInTheDocument();
    expect(screen.getAllByText('Архівований').length).toBeGreaterThan(0);
  });

  async function fillCreateForm() {
    await userEvent.click(await screen.findByRole('button', { name: 'Додати учасника' }));
    await userEvent.type(await screen.findByLabelText(/Логін/), 'fake.new');
    await userEvent.type(screen.getByLabelText(/Прізвище/), 'Новенко');
    await userEvent.type(screen.getByLabelText(/^Ім’я/), 'Нестор');
    await userEvent.click(screen.getByLabelText(/Факультет/, { selector: 'input' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Фейковий факультет', hidden: true }));
    await userEvent.type(screen.getByLabelText(/Навчальна група/), 'НН-11');
    await userEvent.type(screen.getByLabelText(/Telegram/), '@fake_nestor');
    await userEvent.type(screen.getByLabelText(/Дата вступу/), '2031-09-01');
  }

  it('validates required fields client-side before calling the API', async () => {
    renderIn(<MembersPage />, ['members.create']);
    await userEvent.click(await screen.findByRole('button', { name: 'Додати учасника' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Зберегти' }));
    expect((await screen.findAllByText('Це поле обов’язкове.')).length).toBeGreaterThanOrEqual(6);
    expect(api.createMember).not.toHaveBeenCalled();
  });

  it('creates a member and shows the temporary password once, with a copy button', async () => {
    vi.mocked(api.createMember).mockResolvedValue({
      member: member({ ...ANNA, id: 'new', lastName: 'Новенко', firstName: 'Нестор', middleName: '' }),
      temporaryPassword: 'Fake-Temp-Pass-1',
    });
    renderIn(<MembersPage />, ['members.create']);
    await fillCreateForm();
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    await waitFor(() =>
      expect(api.createMember).toHaveBeenCalledWith(
        expect.objectContaining({
          login: 'fake.new',
          lastName: 'Новенко',
          firstName: 'Нестор',
          facultyId: 'f1',
          studyGroup: 'НН-11',
          telegram: '@fake_nestor',
          joinedAt: '2031-09-01',
          locale: 'uk',
        }),
      ),
    );
    expect(await screen.findByDisplayValue('Fake-Temp-Pass-1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Копіювати' })).toBeInTheDocument();
    expect(screen.getByText(/лише раз/)).toBeInTheDocument();
    expect(document.body.textContent).not.toContain('Fake-Temp-Pass-1');
    // Nothing of the password reaches web storage.
    expect(JSON.stringify({ ...localStorage })).not.toContain('Fake-Temp-Pass-1');
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('Fake-Temp-Pass-1');
  });

  it('shows a duplicate login at the field', async () => {
    vi.mocked(api.createMember).mockRejectedValue(new ApiProblemError(409, 'LOGIN_TAKEN'));
    renderIn(<MembersPage />, ['members.create']);
    await fillCreateForm();
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText('Цей логін уже використовується.')).toBeInTheDocument();
  });

  it('shows server validation messages at their fields', async () => {
    vi.mocked(api.createMember).mockRejectedValue(
      new ApiProblemError(400, 'VALIDATION_FAILED', undefined, [{ field: 'studyGroup', message: 'Групу не знайдено' }]),
    );
    renderIn(<MembersPage />, ['members.create']);
    await fillCreateForm();
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText('Групу не знайдено')).toBeInTheDocument();
  });

  it('handles a backend 403 gracefully inside the form', async () => {
    vi.mocked(api.createMember).mockRejectedValue(new ApiProblemError(403, 'FORBIDDEN'));
    renderIn(<MembersPage />, ['members.create']);
    await fillCreateForm();
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText('У вас немає прав на цю дію.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Зберегти' })).toBeEnabled();
  });
});

describe('MemberProfilePage', () => {
  it('shows data and a safe telegram link, but no actions without permissions', async () => {
    renderProfile('m1', ['members.view']);
    expect(await screen.findByRole('heading', { name: 'Тестенко Анна Фейківна' })).toBeInTheDocument();
    expect(screen.getByText('Фейкова біографія')).toBeInTheDocument();
    expect(screen.getByText('05.03.2031')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '@fake_anna' })).toHaveAttribute('href', 'https://t.me/fake_anna');
    for (const name of ['Редагувати', 'Редагувати мій профіль', 'Скинути пароль', 'Архівувати', 'Відновити']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
    expect(screen.getByTestId('history')).toBeInTheDocument();
  });

  it('formats timestamps in Europe/Kyiv', async () => {
    renderProfile('m1', []);
    // 2031-03-05T09:00Z is 11:00 in Kyiv (UTC+2).
    expect(await screen.findByText('05.03.2031, 11:00')).toBeInTheDocument();
  });

  it('offers edit, reset and archive with the matching permissions', async () => {
    renderProfile('m1', ['members.edit', 'members.archive']);
    await screen.findByRole('heading', { name: 'Тестенко Анна Фейківна' });
    expect(screen.getByRole('button', { name: 'Редагувати' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Скинути пароль' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Архівувати' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Відновити' })).not.toBeInTheDocument();
  });

  it('offers only edit with members.edit alone', async () => {
    renderProfile('m1', ['members.edit']);
    await screen.findByRole('heading', { name: 'Тестенко Анна Фейківна' });
    expect(screen.getByRole('button', { name: 'Редагувати' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Архівувати' })).not.toBeInTheDocument();
  });

  it('does not offer archiving of the own account', async () => {
    renderProfile('m1', ['members.archive'], 'm1');
    await screen.findByRole('heading', { name: 'Тестенко Анна Фейківна' });
    expect(screen.queryByRole('button', { name: 'Архівувати' })).not.toBeInTheDocument();
  });

  it('archives after confirmation and reports a backend 403 gracefully', async () => {
    vi.mocked(api.archiveMember).mockRejectedValueOnce(new ApiProblemError(403, undefined));
    renderProfile('m1', ['members.archive']);
    await userEvent.click(await screen.findByRole('button', { name: 'Архівувати' }));
    expect(api.archiveMember).not.toHaveBeenCalled();
    expect(screen.getByText(/Архівувати Тестенко Анна Фейківна\?/)).toBeInTheDocument();
    const dialog = screen.getByRole('dialog');
    const confirm = Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent === 'Архівувати');
    await userEvent.click(confirm as HTMLElement);
    expect(await screen.findByText('У вас немає прав на цю дію.')).toBeInTheDocument();
    expect(api.archiveMember).toHaveBeenCalledWith('m1');
  });

  it('shows an archived member with a notice and a restore action instead of edit', async () => {
    vi.mocked(api.getMember).mockResolvedValue(member(ARCHIVED));
    vi.mocked(api.restoreMember).mockResolvedValue(member({ ...ARCHIVED, archived: false }));
    renderProfile('m3', ['members.edit', 'members.archive']);
    expect(await screen.findByText(/архівовано: вхід заблоковано/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Редагувати' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Архівувати' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Відновити' }));
    const dialog = screen.getByRole('dialog');
    const confirm = Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent === 'Відновити');
    await userEvent.click(confirm as HTMLElement);
    await waitFor(() => expect(api.restoreMember).toHaveBeenCalledWith('m3'));
  });

  it('lets a member edit own telegram and bio only, and shows the language setting', async () => {
    vi.mocked(api.updateSelf).mockResolvedValue(member(ANNA));
    renderProfile('m1', [], 'm1');
    expect(await screen.findByText('Мова інтерфейсу')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: 'Редагувати мій профіль' }));
    expect(screen.queryByLabelText(/Прізвище/)).not.toBeInTheDocument();
    const telegram = screen.getByLabelText(/Telegram/);
    await userEvent.clear(telegram);
    await userEvent.type(telegram, 'abc');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    expect(await screen.findByText(/Використайте 5–32 літери/)).toBeInTheDocument();
    expect(api.updateSelf).not.toHaveBeenCalled();
    await userEvent.clear(telegram);
    await userEvent.type(telegram, '@fake_anna_new');
    await userEvent.click(screen.getByRole('button', { name: 'Зберегти' }));
    await waitFor(() =>
      expect(api.updateSelf).toHaveBeenCalledWith({ telegram: '@fake_anna_new', bio: 'Фейкова біографія', avatarFileId: undefined }),
    );
  });

  it('resets a password after confirmation and shows it once', async () => {
    vi.mocked(api.resetMemberPassword).mockResolvedValue('Fake-Reset-Pass-2');
    renderProfile('m1', ['members.edit']);
    await userEvent.click(await screen.findByRole('button', { name: 'Скинути пароль' }));
    expect(api.resetMemberPassword).not.toHaveBeenCalled();
    const dialog = screen.getByRole('dialog');
    const confirm = Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent === 'Скинути пароль');
    await userEvent.click(confirm as HTMLElement);
    expect(await screen.findByDisplayValue('Fake-Reset-Pass-2')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Я зберіг(-ла) пароль' }));
    await waitFor(() => expect(screen.queryByDisplayValue('Fake-Reset-Pass-2')).not.toBeInTheDocument());
  });

  it('shows a not-found problem for an unknown member', async () => {
    vi.mocked(api.getMember).mockRejectedValue(new ApiProblemError(404, 'NOT_FOUND'));
    renderProfile('missing', []);
    expect(await screen.findByText('Запис більше не існує. Оновіть сторінку.')).toBeInTheDocument();
  });
});
