import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { configure, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { changeLanguage } from '../i18n';
import { renderWithProviders } from '../test/render';
import {
  addAttachmentIds,
  attachmentErrorKey,
  ATTACH_ACCEPT,
  formatFileSize,
  isThumbnailable,
  MAX_TASK_ATTACHMENTS,
  remainingSlots,
} from './attachmentLogic';
import * as filesApi from './attachmentsApi';
import { TaskAttachments } from './TaskAttachments';
import type { Task } from './tasksApi';
import * as api from './tasksApi';

vi.mock('./tasksApi', async (orig) => ({ ...(await orig<typeof api>()), updateTask: vi.fn() }));
vi.mock('./attachmentsApi', () => ({ uploadAttachment: vi.fn() }));

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const TASK: Task = {
  id: 't1',
  spaceId: 'sp1',
  title: 'Фейкова задача',
  statusId: 's1',
  priority: 'HIGH',
  dueDate: '2031-03-05',
  labels: ['тест'],
  attachmentCount: 2,
  authorId: 'author',
  archived: false,
  createdAt: '2031-01-01T10:00:00Z',
  description: null,
  customFieldValues: { f1: 'значення' },
  relatedTaskIds: ['rel1'],
  attachmentIds: ['a1', 'a2'],
  attachments: [
    { id: 'a1', fileName: 'report.pdf', mimeType: 'application/pdf', size: 2048 },
    { id: 'a2', fileName: 'photo.png', mimeType: 'image/png', size: 3 * 1024 * 1024 },
  ],
  suggestDone: false,
};

function renderPanel(editable: boolean, task: Task = TASK) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithProviders(
    <QueryClientProvider client={queryClient}>
      <TaskAttachments task={task} editable={editable} />
    </QueryClientProvider>,
  );
}

const fileInputs = () => Array.from(document.querySelectorAll<HTMLInputElement>('input[type="file"]'));
const png = (name = 'new.png') => new File(['x'], name, { type: 'image/png' });

beforeEach(async () => {
  vi.clearAllMocks();
  await changeLanguage('uk');
});

describe('attachment logic', () => {
  it('formats file sizes', () => {
    expect(formatFileSize(0)).toBe('0 B');
    expect(formatFileSize(1023)).toBe('1023 B');
    expect(formatFileSize(1536)).toBe('1.5 KB');
    expect(formatFileSize(3 * 1024 * 1024)).toBe('3 MB');
    expect(formatFileSize(25 * 1024 * 1024)).toBe('25 MB');
    expect(formatFileSize(-1)).toBe('');
  });

  it('shows thumbnails for browser-safe image types only', () => {
    expect(isThumbnailable('image/png')).toBe(true);
    expect(isThumbnailable('IMAGE/JPEG')).toBe(true);
    expect(isThumbnailable('image/svg+xml')).toBe(false);
    expect(isThumbnailable('application/pdf')).toBe(false);
  });

  it('maps file rejections to localized messages', () => {
    expect(attachmentErrorKey(new ApiProblemError(415, 'FILE_TYPE_NOT_ALLOWED'))).toBe('tasks.attachments.typeNotAllowed');
    expect(attachmentErrorKey(new ApiProblemError(413, 'FILE_TOO_LARGE'))).toBe('problems.FILE_TOO_LARGE');
    expect(attachmentErrorKey(new ApiProblemError(400, 'FILE_EMPTY'))).toBe('problems.FILE_EMPTY');
    expect(attachmentErrorKey(new ApiProblemError(403, undefined))).toBe('problems.FORBIDDEN');
    expect(attachmentErrorKey(new ApiProblemError(422, 'TASK_TOO_MANY_ATTACHMENTS'))).toBe('problems.TASK_TOO_MANY_ATTACHMENTS');
    expect(attachmentErrorKey(new Error('network'))).toBe('problems.UNKNOWN');
  });

  it('merges ids without duplicates and computes the free slots', () => {
    expect(addAttachmentIds(['a', 'b'], ['b', 'c'])).toEqual(['a', 'b', 'c']);
    expect(remainingSlots(18, MAX_TASK_ATTACHMENTS)).toBe(2);
    expect(remainingSlots(25, MAX_TASK_ATTACHMENTS)).toBe(0);
  });

  it('accepts images (camera on phones) among the picker types', () => {
    expect(ATTACH_ACCEPT).toContain('image/*');
  });
});

describe('TaskAttachments', () => {
  it('lists files with size and a thumbnail for images only', () => {
    renderPanel(false);
    expect(screen.getByRole('link', { name: 'report.pdf' })).toHaveAttribute('href', expect.stringContaining('/api/v1/files/a1'));
    expect(screen.getByText('2 KB')).toBeInTheDocument();
    expect(screen.getByText('3 MB')).toBeInTheDocument();
    expect(screen.getAllByTestId('attachment-thumbnail')).toHaveLength(1);
  });

  it('shows the empty state and no controls for a read-only viewer', () => {
    renderPanel(false, { ...TASK, attachmentIds: [], attachments: [] });
    expect(screen.getByText('Файлів не прикріплено.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Прикріпити файли' })).toBeNull();
    expect(fileInputs()).toHaveLength(0);
  });

  it('offers a file picker and a camera picker with image accept', () => {
    renderPanel(true);
    expect(screen.getByRole('button', { name: 'Прикріпити файли' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Зробити фото' })).toBeInTheDocument();
    const [files, camera] = fileInputs();
    expect(files).toHaveAttribute('multiple');
    expect(files.accept).toContain('image/*');
    expect(camera.accept).toBe('image/*');
    expect(camera).toHaveAttribute('capture', 'environment');
  });

  it('uploads a file and attaches it with a single PUT that keeps the other fields', async () => {
    vi.mocked(filesApi.uploadAttachment).mockResolvedValue({ id: 'a3', fileName: 'new.png', mimeType: 'image/png', size: 1 });
    vi.mocked(api.updateTask).mockResolvedValue(TASK);
    renderPanel(true);
    await userEvent.upload(fileInputs()[0], png());
    await waitFor(() => expect(api.updateTask).toHaveBeenCalled());
    const [id, input, related, attachments] = vi.mocked(api.updateTask).mock.calls[0];
    expect(id).toBe('t1');
    expect(related).toEqual(['rel1']);
    expect(attachments).toEqual(['a1', 'a2', 'a3']);
    expect(input).toMatchObject({ title: 'Фейкова задача', priority: 'HIGH', dueDate: '2031-03-05', labels: ['тест'], customFieldValues: { f1: 'значення' } });
  });

  it('shows the localized rejection message and does not attach a rejected file', async () => {
    vi.mocked(filesApi.uploadAttachment).mockRejectedValue(new ApiProblemError(413, 'FILE_TOO_LARGE'));
    renderPanel(true);
    await userEvent.upload(fileInputs()[0], png('big.png'));
    expect(await screen.findByText(/big\.png: Файл завеликий\./)).toBeInTheDocument();
    expect(api.updateTask).not.toHaveBeenCalled();
  });

  it('uses attachment wording for an unsupported type', async () => {
    vi.mocked(filesApi.uploadAttachment).mockRejectedValue(new ApiProblemError(415, 'FILE_TYPE_NOT_ALLOWED'));
    renderPanel(true);
    await userEvent.setup({ applyAccept: false }).upload(fileInputs()[0], new File(['x'], 'run.exe', { type: 'application/x-msdownload' }));
    expect(await screen.findByText(/run\.exe: Цей тип файлу не дозволено\. Використайте зображення, PDF/)).toBeInTheDocument();
  });

  it('keeps the files that uploaded when another one in the batch is rejected', async () => {
    vi.mocked(filesApi.uploadAttachment)
      .mockResolvedValueOnce({ id: 'a3', fileName: 'ok.png', mimeType: 'image/png', size: 1 })
      .mockRejectedValueOnce(new ApiProblemError(400, 'FILE_EMPTY'));
    vi.mocked(api.updateTask).mockResolvedValue(TASK);
    renderPanel(true);
    await userEvent.upload(fileInputs()[0], [png('ok.png'), png('empty.png')]);
    expect(await screen.findByText(/empty\.png: Обраний файл порожній\./)).toBeInTheDocument();
    await waitFor(() => expect(api.updateTask).toHaveBeenCalledTimes(1));
    expect(vi.mocked(api.updateTask).mock.calls[0][3]).toEqual(['a1', 'a2', 'a3']);
  });

  it('shows upload progress while a file is being sent', async () => {
    let report: (fraction: number) => void = () => undefined;
    let finish: (v: { id: string; fileName: string; mimeType: string; size: number }) => void = () => undefined;
    vi.mocked(filesApi.uploadAttachment).mockImplementation(
      (_file, onProgress) =>
        new Promise((resolve) => {
          report = onProgress ?? report;
          finish = resolve;
        }),
    );
    vi.mocked(api.updateTask).mockResolvedValue(TASK);
    renderPanel(true);
    await userEvent.upload(fileInputs()[0], png());
    report(0.4);
    expect(await screen.findByText('Завантаження new.png: 40%')).toBeInTheDocument();
    finish({ id: 'a3', fileName: 'new.png', mimeType: 'image/png', size: 1 });
    await waitFor(() => expect(screen.queryByText(/Завантаження new\.png/)).toBeNull());
  });

  it('removes a file by sending the remaining ids', async () => {
    vi.mocked(api.updateTask).mockResolvedValue(TASK);
    renderPanel(true);
    await userEvent.click(screen.getByRole('button', { name: 'Прибрати report.pdf' }));
    await waitFor(() => expect(api.updateTask).toHaveBeenCalled());
    expect(vi.mocked(api.updateTask).mock.calls[0][3]).toEqual(['a2']);
  });

  it('shows the backend error when attaching fails', async () => {
    vi.mocked(filesApi.uploadAttachment).mockResolvedValue({ id: 'a3', fileName: 'new.png', mimeType: 'image/png', size: 1 });
    vi.mocked(api.updateTask).mockRejectedValue(new ApiProblemError(403, 'TASK_EDIT_FORBIDDEN'));
    renderPanel(true);
    await userEvent.upload(fileInputs()[0], png());
    expect(await screen.findByText('У вас немає прав на цю дію.')).toBeInTheDocument();
  });

  it('blocks further uploads at the attachment limit', () => {
    const ids = Array.from({ length: MAX_TASK_ATTACHMENTS }, (_, i) => `x${i}`);
    renderPanel(true, { ...TASK, attachmentIds: ids, attachments: ids.map((id) => ({ id, fileName: `${id}.txt`, mimeType: 'text/plain', size: 1 })) });
    expect(screen.getByRole('button', { name: 'Прикріпити файли' })).toBeDisabled();
    expect(screen.getByText('Досягнуто ліміт вкладень.')).toBeInTheDocument();
  });
});
