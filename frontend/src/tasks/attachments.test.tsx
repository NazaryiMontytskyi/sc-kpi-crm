import { configure, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { changeLanguage } from '../i18n';
import { renderWithProviders } from '../test/render';
import { AttachmentList, AttachmentUploader } from './Attachments';
import { attachmentErrorKey, ATTACH_ACCEPT, formatFileSize, isThumbnailable, remainingSlots, type AttachmentInfo } from './attachmentLogic';

vi.setConfig({ testTimeout: 30000 });
configure({ asyncUtilTimeout: 8000 });

const FILES: AttachmentInfo[] = [
  { id: 'a1', fileName: 'report.pdf', mimeType: 'application/pdf', size: 2048 },
  { id: 'a2', fileName: 'photo.png', mimeType: 'image/png', size: 3 * 1024 * 1024 },
];

const upload = vi.fn<(file: File, onProgress: (fraction: number) => void) => Promise<AttachmentInfo>>();
const onUploaded = vi.fn<(uploaded: AttachmentInfo[]) => Promise<unknown>>();

function renderUploader(slots = 5) {
  return renderWithProviders(<AttachmentUploader upload={upload} onUploaded={onUploaded} slots={slots} />);
}

const fileInputs = () => Array.from(document.querySelectorAll<HTMLInputElement>('input[type="file"]'));
const png = (name = 'new.png') => new File(['x'], name, { type: 'image/png' });

beforeEach(async () => {
  vi.clearAllMocks();
  onUploaded.mockResolvedValue(undefined);
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
    expect(attachmentErrorKey(new Error('network'))).toBe('problems.UNKNOWN');
  });

  it('computes the free slots', () => {
    expect(remainingSlots(18, 20)).toBe(2);
    expect(remainingSlots(25, 20)).toBe(0);
  });

  it('accepts images (camera on phones) among the picker types', () => {
    expect(ATTACH_ACCEPT).toContain('image/*');
  });
});

describe('AttachmentList', () => {
  it('lists files with size and a thumbnail for images only', () => {
    renderWithProviders(<AttachmentList attachments={FILES} label="Файли" />);
    expect(screen.getByRole('link', { name: 'report.pdf' })).toHaveAttribute('href', expect.stringContaining('/api/v1/files/a1'));
    expect(screen.getByText('2 KB')).toBeInTheDocument();
    expect(screen.getByText('3 MB')).toBeInTheDocument();
    expect(screen.getAllByTestId('attachment-thumbnail')).toHaveLength(1);
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('offers a remove button per file when removing is allowed', async () => {
    const onRemove = vi.fn();
    renderWithProviders(<AttachmentList attachments={FILES} label="Файли" onRemove={onRemove} removeLabel={(name) => `Прибрати ${name}`} />);
    await userEvent.click(screen.getByRole('button', { name: 'Прибрати report.pdf' }));
    expect(onRemove).toHaveBeenCalledWith(FILES[0]);
  });
});

describe('AttachmentUploader', () => {
  it('offers a file picker and a camera picker with image accept', () => {
    renderUploader();
    expect(screen.getByRole('button', { name: 'Прикріпити файли' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Зробити фото' })).toBeInTheDocument();
    const [files, camera] = fileInputs();
    expect(files).toHaveAttribute('multiple');
    expect(files.accept).toContain('image/*');
    expect(camera.accept).toBe('image/*');
    expect(camera).toHaveAttribute('capture', 'environment');
  });

  it('uploads a file and reports it once per batch', async () => {
    upload.mockResolvedValue({ id: 'a3', fileName: 'new.png', mimeType: 'image/png', size: 1 });
    renderUploader();
    await userEvent.upload(fileInputs()[0], png());
    await waitFor(() => expect(onUploaded).toHaveBeenCalledWith([{ id: 'a3', fileName: 'new.png', mimeType: 'image/png', size: 1 }]));
  });

  it('shows the localized rejection message and reports nothing for a rejected file', async () => {
    upload.mockRejectedValue(new ApiProblemError(413, 'FILE_TOO_LARGE'));
    renderUploader();
    await userEvent.upload(fileInputs()[0], png('big.png'));
    expect(await screen.findByText(/big\.png: Файл завеликий\./)).toBeInTheDocument();
    expect(onUploaded).not.toHaveBeenCalled();
  });

  it('uses attachment wording for an unsupported type', async () => {
    upload.mockRejectedValue(new ApiProblemError(415, 'FILE_TYPE_NOT_ALLOWED'));
    renderUploader();
    await userEvent.setup({ applyAccept: false }).upload(fileInputs()[0], new File(['x'], 'run.exe', { type: 'application/x-msdownload' }));
    expect(await screen.findByText(/run\.exe: Цей тип файлу не дозволено\. Використайте зображення, PDF/)).toBeInTheDocument();
  });

  it('keeps the files that uploaded when another one in the batch is rejected', async () => {
    upload
      .mockResolvedValueOnce({ id: 'a3', fileName: 'ok.png', mimeType: 'image/png', size: 1 })
      .mockRejectedValueOnce(new ApiProblemError(400, 'FILE_EMPTY'));
    renderUploader();
    await userEvent.upload(fileInputs()[0], [png('ok.png'), png('empty.png')]);
    expect(await screen.findByText(/empty\.png: Обраний файл порожній\./)).toBeInTheDocument();
    await waitFor(() => expect(onUploaded).toHaveBeenCalledTimes(1));
    expect(onUploaded.mock.calls[0][0].map((a) => a.id)).toEqual(['a3']);
  });

  it('shows upload progress while a file is being sent', async () => {
    let report: (fraction: number) => void = () => undefined;
    let finish: (v: AttachmentInfo) => void = () => undefined;
    upload.mockImplementation(
      (_file, onProgress) =>
        new Promise((resolve) => {
          report = onProgress;
          finish = resolve;
        }),
    );
    renderUploader();
    await userEvent.upload(fileInputs()[0], png());
    report(0.4);
    expect(await screen.findByText('Завантаження new.png: 40%')).toBeInTheDocument();
    finish({ id: 'a3', fileName: 'new.png', mimeType: 'image/png', size: 1 });
    await waitFor(() => expect(screen.queryByText(/Завантаження new\.png/)).toBeNull());
  });

  it('shows the backend error when attaching the uploaded files fails', async () => {
    upload.mockResolvedValue({ id: 'a3', fileName: 'new.png', mimeType: 'image/png', size: 1 });
    onUploaded.mockRejectedValue(new ApiProblemError(403, 'TASK_EDIT_FORBIDDEN'));
    renderUploader();
    await userEvent.upload(fileInputs()[0], png());
    expect(await screen.findByText('У вас немає прав на цю дію.')).toBeInTheDocument();
  });

  it('blocks further uploads at the attachment limit', () => {
    renderUploader(0);
    expect(screen.getByRole('button', { name: 'Прикріпити файли' })).toBeDisabled();
    expect(screen.getByText('Досягнуто ліміт вкладень.')).toBeInTheDocument();
  });
});
