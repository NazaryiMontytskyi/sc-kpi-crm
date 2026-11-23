import { createActor, createRole, token } from '../support/data';
import { expect, test } from '../support/fixtures';
import { createTask, sessionOf } from '../support/scenarios';

test('the notification bell shows a task assignment and opens the task', async ({ admin, openAs }, info) => {
  const creatorRole = await createRole(admin, 'bell-creator', ['tasks.view', 'tasks.create', 'tasks.assign']);
  const assigneeRole = await createRole(admin, 'bell-assignee', ['tasks.view']);
  const creator = await createActor(admin, 'bellcreator', { roleId: creatorRole.id });
  const assignee = await createActor(admin, 'bellassignee', { roleId: assigneeRole.id });
  const title = `E2E task ${token()}`;
  const taskId = await createTask(admin, creator, title);
  await (await sessionOf(creator)).post(`/tasks/${taskId}/assignees`, { userId: assignee.id });

  // Notifications are created by an event handler; wait until the assignee has one.
  const assigneeApi = await sessionOf(assignee);
  await expect
    .poll(async () => (await assigneeApi.get<{ count: number }>('/notifications/unread-count')).count, { timeout: 30_000 })
    .toBeGreaterThan(0);

  const page = await openAs(assignee);
  const bell = page.getByRole('button', { name: /^Notifications, unread: [1-9]/ });
  await expect(bell).toBeVisible();
  await bell.click();
  // The assignee is brand new, so this is the only notification.
  const item = page.getByTestId('notification-item').filter({ hasText: 'You were assigned to a task' });
  await expect(item).toBeVisible();
  await expect(item).toContainText('Assigned to a task');
  await expect(item).toHaveAttribute('data-unread', 'true');
  await item.click();

  // Opening the notification opens the task panel and marks the notification as read.
  await expect(page).toHaveURL(new RegExp(`/tasks/${taskId}$`));
  const mobile = info.project.name === 'mobile-375';
  const panel = mobile ? page.getByRole('dialog', { name: title }) : page.getByRole('region', { name: title });
  await expect(panel).toBeVisible();
  if (mobile) await panel.getByRole('button', { name: '← Back' }).click();
  await expect(page.getByRole('button', { name: 'Notifications, unread: 0' })).toBeVisible();

  // The full list shows it as read.
  await page.goto('/notifications');
  await expect(page.getByTestId('notification-item')).toHaveAttribute('data-unread', 'false');
});
