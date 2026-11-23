import type { Page } from '@playwright/test';
import { createActor, createRole, kyivDate, token } from '../support/data';
import { expect, signIn, test } from '../support/fixtures';
import { sessionOf } from '../support/scenarios';

/**
 * INC-082 navigation smoke: the main page shows "My tasks", the member profile lists the member's tasks, the menu has
 * no retired personal lists and their old URLs open the tracker (spec §7).
 */

async function noHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
}

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`navigation (${colorScheme})`, () => {
    test.use({ colorScheme });

    test('main page, member profile and old personal-list URLs lead to tasks', async ({ page, admin }, info) => {
      const role = await createRole(admin, 'nav', ['tasks.view', 'tasks.create', 'dashboard.view', 'members.view']);
      const member = await createActor(admin, 'nav', { roleId: role.id });
      const space = await admin.post<{ id: string }>('/spaces', { name: `E2E nav space ${token()}`, general: true });
      const session = await sessionOf(member);
      const title = `E2E nav task ${token()}`;
      const task = await session.post<{ id: string }>('/tasks', { spaceId: space.id, title, deadline: kyivDate(2) });
      await session.post(`/tasks/${task.id}/assignees`, { userId: member.id });

      // Main page: the "My tasks" widget lists the task I am an assignee of.
      await signIn(page, member);
      await expect(page.locator('html')).toHaveAttribute('data-mantine-color-scheme', colorScheme);
      const widget = page.getByTestId('my-tasks-widget');
      await expect(widget.getByRole('heading', { name: 'My tasks' })).toBeVisible();
      await expect(widget.getByTestId('dashboard-value-active')).toHaveText('1');
      await expect(widget.getByRole('link', { name: title })).toBeVisible();
      await noHorizontalScroll(page);

      // The menu no longer lists the retired personal lists.
      await expect(page.getByRole('link', { name: 'My resolutions' })).toHaveCount(0);

      // The task opens in the tracker panel from the widget.
      await widget.getByRole('link', { name: title }).click();
      await expect(page).toHaveURL(new RegExp(`/tasks/${task.id}$`));
      const mobile = info.project.name === 'mobile-375';
      const panel = mobile ? page.getByRole('dialog', { name: title }) : page.getByRole('region', { name: title });
      await expect(panel).toBeVisible();
      await expect(panel.getByRole('tab', { name: 'Comments' })).toBeVisible();
      await expect(panel.getByRole('tab', { name: /Resolutions/ })).toHaveCount(0);

      // Member profile: the member's open tasks.
      await page.goto(`/members/${member.id}`);
      const section = page.getByTestId('member-tasks');
      await expect(section.getByRole('heading', { name: 'Tasks' })).toBeVisible();
      await expect(section.getByTestId('count-active')).toContainText('1');
      await expect(section.getByRole('link', { name: title })).toBeVisible();
      await noHorizontalScroll(page);

      // Old personal-list and item URLs redirect into the tracker.
      await page.goto('/resolutions/mine?filter=ACTIVE');
      await expect(page).toHaveURL(/\/tasks$/);
      await page.goto('/resolutions/issued?filter=SUBMITTED');
      await expect(page).toHaveURL(/\/tasks\?section=issued$/);
      await page.goto(`/resolutions/${task.id}`);
      await expect(page).toHaveURL(new RegExp(`/tasks/${task.id}$`));
      await expect(mobile ? page.getByRole('dialog', { name: title }) : page.getByRole('region', { name: title })).toBeVisible();
    });
  });
}
