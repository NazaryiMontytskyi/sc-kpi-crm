import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CurrentUserProvider, STUB_AUTH_STATE, type AuthState } from '../auth/CurrentUser';
import { changeLanguage } from '../i18n';
import * as memberApi from '../members/memberApi';
import { renderWithProviders } from '../test/render';
import { canModifyComment, canSubmitComment, commentErrorKey, extractMentionIds, filterMentionCandidates } from './commentLogic';
import * as api from './commentsApi';
import { CommentsPanel } from './CommentsPanel';

vi.mock('./commentsApi', async (orig) => ({
  ...(await orig<typeof api>()),
  listComments: vi.fn(),
  createComment: vi.fn(),
  editComment: vi.fn(),
  archiveComment: vi.fn(),
  uploadCommentFile: vi.fn(),
}));
vi.mock('../members/memberApi', async (orig) => ({ ...(await orig<typeof memberApi>()), listMembers: vi.fn() }));

// jsdom has no layout; ProseMirror asks ranges and text nodes for their rectangles when it scrolls to the cursor.
const noRects = () => Object.assign([], { item: () => null }) as unknown as DOMRectList;
const noRect = () => new DOMRect(0, 0, 0, 0);
Range.prototype.getClientRects = noRects;
Range.prototype.getBoundingClientRect = noRect;

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const member = (id: string, lastName: string, over: Partial<memberApi.MemberSummary> = {}): memberApi.MemberSummary => ({
  id,
  lastName,
  firstName: 'Фейк',
  middleName: '',
  faculty: null,
  studyGroup: '',
  telegram: '',
  joinedAt: '2031-01-01',
  avatarFileId: null,
  archived: false,
  ...over,
});
const ALICE = member('alice', 'Фейкова');
const BOB = member('bob', 'Фейковий');
const GONE = member('gone', 'Архівний', { archived: true });

const doc = (text: string) => ({ type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] });
const comment = (id: string, authorId: string, text: string, over: Partial<api.Comment> = {}): api.Comment => ({
  id,
  targetType: 'TASK',
  targetId: 't1',
  authorId,
  body: doc(text),
  mentionUserIds: [],
  attachments: [],
  edited: false,
  createdAt: '2031-01-01T10:00:00Z',
  ...over,
});

const authWith = (id: string, isAdmin = false): AuthState => ({
  ...STUB_AUTH_STATE,
  user: { id, displayName: 'Test User', isAdmin, permissions: ['tasks.view'] },
});

function renderPanel(auth: AuthState, readOnly = false) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <CurrentUserProvider value={auth}>
        <CommentsPanel targetType="TASK" targetId="t1" readOnly={readOnly} />
      </CurrentUserProvider>
    </QueryClientProvider>,
  );
}

const page = (items: api.Comment[]): api.CommentPage => ({ items, page: 0, totalPages: 1, totalElements: items.length });

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
  vi.mocked(memberApi.listMembers).mockResolvedValue([ALICE, BOB, GONE]);
  vi.mocked(api.listComments).mockResolvedValue(page([]));
});

describe('comment logic', () => {
  it('extracts mention user ids from the document without duplicates', () => {
    const body = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            { type: 'text', text: 'hi ' },
            { type: 'mention', attrs: { id: 'a', label: 'A' } },
            { type: 'mention', attrs: { id: 'b', label: 'B' } },
            { type: 'mention', attrs: { id: 'a', label: 'A' } },
          ],
        },
        { type: 'bulletList', content: [{ type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'mention', attrs: { id: 'c' } }] }] }] },
      ],
    };
    expect(extractMentionIds(body)).toEqual(['a', 'b', 'c']);
    expect(extractMentionIds(undefined)).toEqual([]);
    expect(extractMentionIds(doc('plain'))).toEqual([]);
  });

  it('offers edit and delete to the author or an Admin only', () => {
    expect(canModifyComment(authWith('alice').user, { authorId: 'alice' })).toBe(true);
    expect(canModifyComment(authWith('bob').user, { authorId: 'alice' })).toBe(false);
    expect(canModifyComment(authWith('bob', true).user, { authorId: 'alice' })).toBe(true);
    expect(canModifyComment(null, { authorId: 'alice' })).toBe(false);
  });

  it('allows sending text or an attachment, not nothing', () => {
    expect(canSubmitComment(true, 0)).toBe(false);
    expect(canSubmitComment(false, 0)).toBe(true);
    expect(canSubmitComment(true, 1)).toBe(true);
  });

  it('filters mention candidates by name, case-insensitively, and limits them', () => {
    expect(filterMentionCandidates([ALICE, BOB], 'фейкова').map((m) => m.id)).toEqual(['alice']);
    expect(filterMentionCandidates([ALICE, BOB], '').map((m) => m.id)).toEqual(['alice', 'bob']);
    expect(filterMentionCandidates([ALICE, BOB], '', 1)).toHaveLength(1);
    expect(filterMentionCandidates([ALICE, BOB], 'zzz')).toEqual([]);
  });

  it('maps a 403 to a permission message and keeps known problem codes', () => {
    expect(commentErrorKey(new ApiProblemError(403, 'COMMENT_EDIT_FORBIDDEN'))).toBe('problems.COMMENT_EDIT_FORBIDDEN');
    expect(commentErrorKey(new ApiProblemError(403, undefined))).toBe('problems.FORBIDDEN');
    expect(commentErrorKey(new ApiProblemError(422, 'COMMENT_MENTION_INVALID'))).toBe('problems.COMMENT_MENTION_INVALID');
  });
});

describe('CommentsPanel', () => {
  it('shows the thread with author names', async () => {
    vi.mocked(api.listComments).mockResolvedValue(
      page([comment('c1', 'alice', 'Перший коментар'), comment('c2', 'bob', 'Другий коментар', { edited: true })]),
    );
    renderPanel(authWith('zed'));
    expect(await screen.findByText('Перший коментар')).toBeInTheDocument();
    expect(screen.getByText('Другий коментар')).toBeInTheDocument();
    expect(await screen.findByText('Фейкова Фейк')).toBeInTheDocument();
    expect(screen.getByText(/відредаговано/)).toBeInTheDocument();
    // a user who is neither author nor Admin sees no edit/delete
    expect(screen.queryByRole('button', { name: 'Редагувати' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Видалити' })).toBeNull();
  });

  it('shows the empty state', async () => {
    renderPanel(authWith('zed'));
    expect(await screen.findByText('Коментарів ще немає.')).toBeInTheDocument();
  });

  it('shows edit and delete for own comments and for an Admin', async () => {
    vi.mocked(api.listComments).mockResolvedValue(page([comment('c1', 'alice', 'Мій коментар'), comment('c2', 'bob', 'Чужий коментар')]));
    const { unmount } = renderPanel(authWith('alice'));
    await screen.findByText('Мій коментар');
    expect(screen.getAllByRole('button', { name: 'Редагувати' })).toHaveLength(1);
    unmount();
    renderPanel(authWith('zed', true));
    await screen.findByText('Мій коментар');
    expect(screen.getAllByRole('button', { name: 'Редагувати' })).toHaveLength(2);
  });

  it('deletes after confirmation', async () => {
    vi.mocked(api.listComments).mockResolvedValue(page([comment('c1', 'alice', 'Мій коментар')]));
    vi.mocked(api.archiveComment).mockResolvedValue(comment('c1', 'alice', 'Мій коментар'));
    renderPanel(authWith('alice'));
    await screen.findByText('Мій коментар');
    await userEvent.click(screen.getByRole('button', { name: 'Видалити' }));
    expect(api.archiveComment).not.toHaveBeenCalled();
    const dialog = await screen.findByRole('alertdialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Видалити' }));
    await waitFor(() => expect(vi.mocked(api.archiveComment).mock.calls[0]?.[0]).toBe('c1'));
  });

  it('sends a comment with the mention picked from the member directory', async () => {
    vi.mocked(api.createComment).mockResolvedValue(comment('c9', 'zed', 'x'));
    renderPanel(authWith('zed'));
    await screen.findByText('Коментарів ще немає.');
    expect(screen.getByRole('button', { name: 'Коментувати' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: /Згадати/ }));
    // archived members are not offered
    await screen.findByRole('button', { name: 'Фейковий Фейк' });
    expect(screen.queryByRole('button', { name: 'Архівний Фейк' })).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: 'Фейковий Фейк' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Коментувати' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Коментувати' }));
    await waitFor(() => expect(api.createComment).toHaveBeenCalled());
    const [type, target, input] = vi.mocked(api.createComment).mock.calls[0];
    expect(type).toBe('TASK');
    expect(target).toBe('t1');
    expect(input.mentionUserIds).toEqual(['bob']);
    expect(JSON.stringify(input.body)).toContain('"type":"mention"');
  });

  it('hides the composer for a read-only (archived) task', async () => {
    renderPanel(authWith('zed'), true);
    await screen.findByText('Коментарів ще немає.');
    expect(screen.queryByRole('button', { name: 'Коментувати' })).toBeNull();
  });
});
