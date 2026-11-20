import type { Page } from '@playwright/test';
import { createActor, createRole, kyivDate, token, type Actor } from '../support/data';
import { expect, signIn, test } from '../support/fixtures';
import { sessionOf } from '../support/scenarios';
import type { ApiSession } from '../support/api';

/**
 * INC-077: master/detail tracker. On the desktop project (1280 px) the task opens in a side panel next to the list;
 * on the 375 px project it opens in a full-screen sheet with a back control. Both run in the light and dark theme.
 */

interface Seeded {
  taskId: string;
  title: string;
  subtaskTitle: string;
  linkTitle: string;
}

/** A task created by `author` in a fresh general space, with the author assigned, one subtask and one link. */
async function seedTask(admin: ApiSession, author: Actor): Promise<Seeded> {
  const space = await admin.post<{ id: string }>('/spaces', { name: `E2E panel space ${token()}`, general: true });
  const session = await sessionOf(author);
  const title = `E2E panel task ${token()}`;
  const task = await session.post<{ id: string }>('/tasks', { spaceId: space.id, title, deadline: kyivDate(5) });
  await session.post(`/tasks/${task.id}/assignees`, { userId: author.id });
  const subtaskTitle = `E2E subtask ${token()}`;
  await session.post(`/tasks/${task.id}/subtasks`, { title: subtaskTitle, deadline: kyivDate(3) });
  const linkTitle = `Fake agenda ${token()}`;
  await session.post(`/tasks/${task.id}/artifacts`, { type: 'LINK', url: 'https://example.org/fake-agenda', title: linkTitle });
  return { taskId: task.id, title, subtaskTitle, linkTitle };
}

async function noHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
}

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`task panel (${colorScheme})`, () => {
    test.use({ colorScheme });

    test('opens a task from the list in place, shows its fields and closes with back', async ({ page, admin }, info) => {
      const role = await createRole(admin, 'panel', ['tasks.view', 'tasks.create']);
      const author = await createActor(admin, 'panel', { roleId: role.id });
      const seeded = await seedTask(admin, author);

      await signIn(page, author);
      await page.goto('/tasks');
      await expect(page.locator('html')).toHaveAttribute('data-mantine-color-scheme', colorScheme);
      const link = page.getByTestId('task-list').getByRole('link', { name: seeded.title });
      await link.click();
      await expect(page).toHaveURL(new RegExp(`/tasks/${seeded.taskId}$`));

      const mobile = info.project.name === 'mobile-375';
      const panel = mobile ? page.getByRole('dialog', { name: seeded.title }) : page.getByRole('region', { name: seeded.title });
      await expect(panel).toBeVisible();
      await expect(page.getByTestId('task-panel')).toHaveAttribute('data-variant', mobile ? 'sheet' : 'side');

      // Every field of spec UI-2 in one view.
      await expect(panel.getByLabel('Status')).toBeVisible();
      await expect(panel.getByTestId('panel-assignees')).toContainText(author.fullName);
      await expect(panel.getByTestId('panel-issued')).not.toBeEmpty();
      await expect(panel.getByTestId('panel-deadline')).not.toBeEmpty();
      await expect(panel.getByRole('heading', { name: 'Description' })).toBeVisible();
      await expect(panel.getByRole('link', { name: seeded.linkTitle })).toHaveAttribute('href', 'https://example.org/fake-agenda');
      await expect(panel.getByTestId('panel-subtask')).toContainText(seeded.subtaskTitle);
      await expect(panel.getByRole('tab', { name: 'Comments' })).toBeVisible();

      if (mobile) {
        // Full-screen sheet: back control, focus inside, nothing scrolls sideways.
        const box = await page.getByTestId('task-panel').boundingBox();
        expect(box?.width).toBeGreaterThanOrEqual(370);
        await noHorizontalScroll(page);
        const back = panel.getByRole('button', { name: '← Back' });
        await expect(back).toBeFocused();
        const backBox = await back.boundingBox();
        expect(backBox?.height).toBeGreaterThanOrEqual(44);
        await back.click();
      } else {
        // The list stays next to the panel; the browser back button closes it.
        await expect(link).toBeVisible();
        await expect(link).toHaveAttribute('aria-current', 'true');
        await page.goBack();
      }

      await expect(page.getByTestId('task-panel')).toHaveCount(0);
      await expect(page).toHaveURL(/\/tasks$/);
      await expect(link).toBeFocused();
      await noHorizontalScroll(page);
    });

    test('a deep link opens the panel on a cold load', async ({ page, admin }, info) => {
      const role = await createRole(admin, 'panel-deep', ['tasks.view', 'tasks.create']);
      const author = await createActor(admin, 'paneldeep', { roleId: role.id });
      const seeded = await seedTask(admin, author);

      await signIn(page, author);
      await page.goto(`/tasks/${seeded.taskId}`);
      const mobile = info.project.name === 'mobile-375';
      const panel = mobile ? page.getByRole('dialog', { name: seeded.title }) : page.getByRole('region', { name: seeded.title });
      await expect(panel).toBeVisible();
      await expect(panel.getByTestId('panel-subtask')).toContainText(seeded.subtaskTitle);

      // A subtask opens in the same panel, and closing goes back to the tracker.
      await panel.getByRole('link', { name: seeded.subtaskTitle }).click();
      const subPanel = mobile ? page.getByRole('dialog', { name: seeded.subtaskTitle }) : page.getByRole('region', { name: seeded.subtaskTitle });
      await expect(subPanel).toBeVisible();
      await expect(subPanel.getByRole('link', { name: '↑ To the parent task' })).toBeVisible();
      if (mobile) await subPanel.getByRole('button', { name: '← Back' }).click();
      else await subPanel.getByRole('button', { name: 'Close task panel' }).click();
      await expect(page.getByTestId('task-panel')).toHaveCount(0);
      await expect(page).toHaveURL(/\/tasks$/);
    });
  });
}

test('a kanban card opens the same panel', async ({ page, admin }, info) => {
  const role = await createRole(admin, 'panel-board', ['tasks.view', 'tasks.create']);
  const author = await createActor(admin, 'panelboard', { roleId: role.id });
  const space = await admin.post<{ id: string }>('/spaces', { name: `E2E board space ${token()}`, general: true });
  const title = `E2E board task ${token()}`;
  const task = await (await sessionOf(author)).post<{ id: string }>('/tasks', { spaceId: space.id, title });

  await signIn(page, author);
  await page.goto(`/tasks/board?space=${space.id}`);
  await expect(page).toHaveURL(new RegExp(`/tasks\\?layout=board&space=${space.id}`));
  await page.getByTestId('board-card').getByRole('link', { name: title }).click();
  await expect(page).toHaveURL(new RegExp(`/tasks/${task.id}\\?layout=board&space=${space.id}`));
  const mobile = info.project.name === 'mobile-375';
  const panel = mobile ? page.getByRole('dialog', { name: title }) : page.getByRole('region', { name: title });
  await expect(panel).toBeVisible();
  if (!mobile) await expect(page.getByTestId('board')).toBeVisible();
});
