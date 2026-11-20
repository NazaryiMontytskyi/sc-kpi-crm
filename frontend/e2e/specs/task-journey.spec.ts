import type { Page } from '@playwright/test';
import { createActor, createRole, kyivDate, token } from '../support/data';
import { expect, signIn, test } from '../support/fixtures';
import { sessionOf } from '../support/scenarios';

/**
 * INC-078: one user journey through the tracker with inline quick actions (spec UI-2, UI-5, ST-2). Browse the list,
 * open a task in the panel (side panel on desktop, full-screen sheet at 375 px), comment, change the status without a
 * dialog and add a subtask for oneself in place. Runs in the light and the dark theme.
 */

interface SpaceStatus {
  id: string;
  name: string;
  category: 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELED';
}

async function noHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
}

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`task journey (${colorScheme})`, () => {
    test.use({ colorScheme });

    test('browse -> open -> comment -> change status -> add subtask', async ({ page, admin }, info) => {
      const role = await createRole(admin, 'journey', ['tasks.view', 'tasks.create']);
      const author = await createActor(admin, 'journey', { roleId: role.id });
      const space = await admin.post<{ id: string; statuses: SpaceStatus[] }>('/spaces', {
        name: `E2E journey space ${token()}`,
        general: true,
      });
      const inProgress = space.statuses.find((s) => s.category === 'IN_PROGRESS');
      expect(inProgress).toBeDefined();
      const title = `E2E journey task ${token()}`;
      const session = await sessionOf(author);
      const task = await session.post<{ id: string }>('/tasks', { spaceId: space.id, title, deadline: kyivDate(7) });

      // Browse: the task is in the list.
      await signIn(page, author);
      await page.goto('/tasks?section=all');
      await expect(page.locator('html')).toHaveAttribute('data-mantine-color-scheme', colorScheme);
      const link = page.getByTestId('task-list').getByRole('link', { name: title });
      await expect(link).toBeVisible();

      // Open: the panel shows the task in place.
      await link.click();
      await expect(page).toHaveURL(new RegExp(`/tasks/${task.id}\\?section=all$`));
      const mobile = info.project.name === 'mobile-375';
      const panel = mobile ? page.getByRole('dialog', { name: title }) : page.getByRole('region', { name: title });
      await expect(panel).toBeVisible();

      // Comment.
      const comment = `Fake progress note ${token()}`;
      const composer = panel.getByRole('textbox', { name: 'Your comment' });
      await composer.click();
      await page.keyboard.type(comment);
      await panel.getByRole('button', { name: 'Comment', exact: true }).click();
      await expect(panel.getByText(comment)).toBeVisible();

      // Change the status without a dialog.
      const status = panel.getByLabel('Status', { exact: true });
      await status.click();
      await page.getByRole('option', { name: inProgress?.name ?? '', exact: true }).click();
      await expect(status).toHaveValue(inProgress?.name ?? '');
      await expect
        .poll(async () => (await session.get<{ statusId: string }>(`/tasks/${task.id}`)).statusId)
        .toBe(inProgress?.id);

      // Add a subtask for oneself in place; "for someone" needs tasks.assign, which this role lacks.
      const subtaskTitle = `E2E inline subtask ${token()}`;
      await panel.getByRole('button', { name: 'New subtask' }).click();
      const form = panel.getByTestId('subtask-form');
      await expect(form.getByText('The subtask will be assigned to you.')).toBeVisible();
      await expect(form.getByText('For someone else')).toHaveCount(0);
      await form.getByLabel(/^Subtask title/).fill(subtaskTitle);
      await form.getByRole('button', { name: 'Add subtask' }).click();
      await expect(panel.getByTestId('panel-subtask').filter({ hasText: subtaskTitle })).toBeVisible();
      const detail = await session.get<{ subtasks: Array<{ title: string; assigneeIds: string[] }> }>(`/tasks/${task.id}`);
      expect(detail.subtasks.find((s) => s.title === subtaskTitle)?.assigneeIds).toEqual([author.id]);

      if (mobile) await noHorizontalScroll(page);
    });
  });
}
