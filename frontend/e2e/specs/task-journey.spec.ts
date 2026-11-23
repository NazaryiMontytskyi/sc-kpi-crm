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
      const status = panel.getByRole('combobox', { name: 'Status', exact: true });
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

    /**
     * INC-083: the full journey of the redesigned tracker (spec §9.2, §9.3): assign a colleague, add a subtask for
     * them, attach a link and a file, finish the subtask and see the close suggestion, close the panel from the
     * keyboard (desktop: Esc, spec UI-7) or with the back control (375 px sheet), and find the task in the
     * colleague's "My tasks".
     */
    test('full journey: assign, subtask for someone, artifacts, suggest close, close, colleague sees it', async ({
      page,
      admin,
      openAs,
    }, info) => {
      const creatorRole = await createRole(admin, 'full-creator', ['tasks.view', 'tasks.create', 'tasks.assign']);
      const colleagueRole = await createRole(admin, 'full-colleague', ['tasks.view', 'tasks.create']);
      const creator = await createActor(admin, 'fullcreator', { roleId: creatorRole.id });
      const colleague = await createActor(admin, 'fullcolleague', { roleId: colleagueRole.id });
      const space = await admin.post<{ id: string; statuses: SpaceStatus[] }>('/spaces', {
        name: `E2E full journey space ${token()}`,
        general: true,
      });
      const title = `E2E full journey task ${token()}`;
      const session = await sessionOf(creator);
      const task = await session.post<{ id: string }>('/tasks', { spaceId: space.id, title, deadline: kyivDate(10) });

      await signIn(page, creator);
      await page.goto('/tasks?section=all');
      await expect(page.locator('html')).toHaveAttribute('data-mantine-color-scheme', colorScheme);
      const link = page.getByTestId('task-list').getByRole('link', { name: title });
      await link.click();
      await expect(page).toHaveURL(new RegExp(`/tasks/${task.id}\\?section=all$`));
      const mobile = info.project.name === 'mobile-375';
      const panel = mobile ? page.getByRole('dialog', { name: title }) : page.getByRole('region', { name: title });
      await expect(panel).toBeVisible();

      // Assign the colleague inline (tasks.assign).
      const picker = panel.getByRole('combobox', { name: 'Add assignee' }).or(panel.getByRole('textbox', { name: 'Add assignee' }));
      await picker.click();
      await picker.fill(colleague.lastName);
      await page.getByRole('option', { name: colleague.fullName }).click();
      await expect(panel.getByTestId('panel-assignees')).toContainText(colleague.fullName);
      await expect
        .poll(async () =>
          (await session.get<{ assignees: Array<{ userId: string; active: boolean }> }>(`/tasks/${task.id}`)).assignees
            .filter((a) => a.active)
            .map((a) => a.userId),
        )
        .toEqual([colleague.id]);

      // A subtask for someone else, with a deadline before the parent's.
      const subtaskTitle = `E2E subtask for colleague ${token()}`;
      await panel.getByRole('button', { name: 'New subtask' }).click();
      const form = panel.getByTestId('subtask-form');
      await form.getByLabel(/^Subtask title/).fill(subtaskTitle);
      await form.getByLabel(/^Subtask deadline/).fill(kyivDate(5));
      await form.getByText('For someone else').click();
      const subAssignee = form.getByRole('combobox', { name: /^Assignee/ }).or(form.getByRole('textbox', { name: /^Assignee/ }));
      await subAssignee.click();
      await subAssignee.fill(colleague.lastName);
      await page.getByRole('option', { name: colleague.fullName }).click();
      await form.getByRole('button', { name: 'Add subtask' }).click();
      const subtaskRow = panel.getByTestId('panel-subtask').filter({ hasText: subtaskTitle });
      await expect(subtaskRow).toBeVisible();
      const detail = await session.get<{ subtasks: Array<{ title: string; assigneeIds: string[]; deadline: string }> }>(
        `/tasks/${task.id}`,
      );
      const created = detail.subtasks.find((s) => s.title === subtaskTitle);
      expect(created?.assigneeIds).toEqual([colleague.id]);
      expect(created?.deadline).toBe(kyivDate(5));

      // Artifacts: a link and a small fake text file.
      const linkTitle = `Fake minutes ${token()}`;
      await panel.getByRole('button', { name: 'Add link' }).click();
      const linkForm = panel.getByTestId('artifact-link-form');
      await linkForm.getByLabel(/^Link/).fill('https://example.org/fake-minutes');
      await linkForm.getByLabel(/^Title/).fill(linkTitle);
      await linkForm.getByRole('button', { name: 'Attach link' }).click();
      await expect(panel.getByRole('link', { name: linkTitle })).toHaveAttribute('href', 'https://example.org/fake-minutes');
      const fileName = `fake-report-${token()}.txt`;
      const artifacts = panel.getByRole('region', { name: 'Artifacts' });
      await artifacts.locator('input[type="file"]:not([capture])').setInputFiles({
        name: fileName,
        mimeType: 'text/plain',
        buffer: Buffer.from('Fake report for the end-to-end suite. No real data.\n'),
      });
      await expect(panel.getByTestId('panel-artifact').filter({ hasText: fileName })).toBeVisible();
      await expect(panel.getByTestId('panel-artifact')).toHaveCount(2);

      // Finishing the only subtask makes the panel suggest closing the parent, without changing it.
      // The checkbox follows the saved status, so click and wait for it rather than check().
      const doneBox = subtaskRow.getByRole('checkbox', { name: `“${subtaskTitle}” done` });
      await doneBox.click();
      await expect(doneBox).toBeChecked();
      await expect(panel.getByTestId('suggest-close')).toBeVisible();
      const todo = space.statuses.find((s) => s.category === 'TODO');
      expect((await session.get<{ statusId: string }>(`/tasks/${task.id}`)).statusId).toBe(todo?.id);

      if (mobile) {
        await noHorizontalScroll(page);
        await panel.getByRole('button', { name: '← Back' }).click();
      } else {
        // Keyboard (spec UI-7): Esc closes the side panel and the focus goes back to the card.
        await page.getByRole('heading', { name: title }).focus();
        await page.keyboard.press('Escape');
      }
      await expect(page.getByTestId('task-panel')).toHaveCount(0);
      await expect(page).toHaveURL(/\/tasks\?section=all$/);
      await expect(link).toBeFocused();

      // The colleague finds the task in "My tasks" (the default section) and opens it.
      const other = await openAs(colleague);
      await other.goto('/tasks');
      const mine = other.getByRole('link', { name: title });
      await expect(mine).toBeVisible();
      await mine.click();
      const otherPanel = mobile ? other.getByRole('dialog', { name: title }) : other.getByRole('region', { name: title });
      await expect(otherPanel.getByTestId('panel-assignees')).toContainText(colleague.fullName);
      await expect(otherPanel.getByTestId('panel-subtask')).toContainText(subtaskTitle);
      if (mobile) await noHorizontalScroll(other);
    });
  });
}

/**
 * INC-083, spec UI-7: on the desktop the arrow keys move through the task list and, with the side panel open, the
 * panel follows the focus; Esc closes it. (Phones have no side panel: the sheet is modal and closes with Esc/back.)
 */
test('keyboard: arrows move through the list, the side panel follows, Esc closes it', async ({ page, admin }, info) => {
  test.skip(info.project.name === 'mobile-375', 'the side panel exists on the desktop only');
  const role = await createRole(admin, 'keys', ['tasks.view', 'tasks.create']);
  const author = await createActor(admin, 'keys', { roleId: role.id });
  const space = await admin.post<{ id: string }>('/spaces', { name: `E2E keys space ${token()}`, general: true });
  const session = await sessionOf(author);
  const stem = `E2E keys ${token()}`;
  const created = [`${stem} a`, `${stem} b`, `${stem} c`];
  for (const title of created) await session.post('/tasks', { spaceId: space.id, title });
  // The list shows the newest first.
  const titles = [...created].reverse();

  await signIn(page, author);
  await page.goto(`/tasks?section=all&space=${space.id}`);
  const list = page.getByTestId('task-list');
  await expect(list.getByRole('link')).toHaveText(titles);

  await list.getByRole('link', { name: titles[0] }).click();
  await expect(page.getByRole('region', { name: titles[0] })).toBeVisible();
  await list.getByRole('link', { name: titles[0] }).focus();
  await page.keyboard.press('ArrowDown');
  await expect(page.getByRole('region', { name: titles[1] })).toBeVisible();
  await expect(list.getByRole('link', { name: titles[1] })).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await expect(page.getByRole('region', { name: titles[2] })).toBeVisible();
  await page.keyboard.press('ArrowUp');
  await expect(page.getByRole('region', { name: titles[1] })).toBeVisible();
  await expect(list.getByRole('link', { name: titles[1] })).toBeFocused();

  await page.keyboard.press('Escape');
  await expect(page.getByTestId('task-panel')).toHaveCount(0);
  await expect(page).toHaveURL(/\/tasks\?section=all/);
  await expect(list.getByRole('link', { name: titles[1] })).toBeFocused();
});
