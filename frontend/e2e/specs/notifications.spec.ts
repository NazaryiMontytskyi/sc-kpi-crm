import { createActor, createRole, kyivDate, token } from '../support/data';
import { expect, test } from '../support/fixtures';
import { createTask, sessionOf } from '../support/scenarios';

test('the notification bell shows a new resolution and opens it', async ({ admin, openAs }) => {
  const issuerRole = await createRole(admin, 'bell-issuer', ['tasks.view', 'tasks.create', 'resolutions.create']);
  const executorRole = await createRole(admin, 'bell-exec', ['tasks.view']);
  const author = await createActor(admin, 'bellauthor', { roleId: issuerRole.id });
  const executor = await createActor(admin, 'bellexec', { roleId: executorRole.id });
  const taskId = await createTask(admin, author, `E2E task ${token()}`);
  const instruction = `Review the fake budget ${token()}`;
  await (await sessionOf(author)).post(`/tasks/${taskId}/resolutions`, {
    executorId: executor.id,
    instruction,
    dueDate: kyivDate(5),
  });

  // Notifications are created by an event handler; wait until the executor has one.
  const executorApi = await sessionOf(executor);
  await expect
    .poll(async () => (await executorApi.get<{ count: number }>('/notifications/unread-count')).count, { timeout: 30_000 })
    .toBeGreaterThan(0);

  const page = await openAs(executor);
  const bell = page.getByRole('button', { name: /^Notifications, unread: [1-9]/ });
  await expect(bell).toBeVisible();
  await bell.click();
  // The executor is brand new, so this is the only notification.
  const item = page.getByTestId('notification-item').filter({ hasText: 'A resolution was issued to you' });
  await expect(item).toBeVisible();
  await expect(item).toContainText('New resolution');
  await expect(item).toHaveAttribute('data-unread', 'true');
  await item.click();

  // Opening the notification leads to the resolution and marks the notification as read.
  await expect(page).toHaveURL(new RegExp(`/tasks/${taskId}`));
  await expect(page.getByTestId('resolution').filter({ hasText: instruction })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Notifications, unread: 0' })).toBeVisible();

  // The full list shows it as read.
  await page.goto('/notifications');
  await expect(page.getByTestId('notification-item')).toHaveAttribute('data-unread', 'false');
});
