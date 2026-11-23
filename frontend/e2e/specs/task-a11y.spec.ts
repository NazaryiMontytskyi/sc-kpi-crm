import AxeBuilder from '@axe-core/playwright';
import type { Page } from '@playwright/test';
import { createActor, createRole, kyivDate, token } from '../support/data';
import { expect, signIn, test } from '../support/fixtures';
import { sessionOf } from '../support/scenarios';

/**
 * INC-083 a11y audit of the task panel (spec UI-8, §7: WCAG AA in light and dark). axe-core checks the side panel on
 * the desktop project and the full-screen sheet on the 375 px project, in both colour schemes, with every block of
 * the panel filled (assignees, deadline, description, artifacts, subtasks, comments, suggest-close banner). A
 * violation with impact "serious" or "critical" fails the test; minor/moderate ones are attached to the report.
 */

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

/**
 * Known violations of the shared rich-text component (`frontend/src/shared/RichTextField.tsx`, outside the tasks
 * feature; reported for a shared-code fix in INC-083): the read-only `RichTextView` renders a TipTap
 * `.ProseMirror[contenteditable="false"]` with an input role and no name, and the composer toolbar shows "1." for a
 * button named "Numbered list". Only these exact rule + node combinations are tolerated; any other serious
 * violation, or the same rule on another node, fails the audit. Remove the entries once the shared fix lands.
 */
const KNOWN_SHARED: Array<{ rule: string; node: RegExp }> = [
  { rule: 'aria-input-field-name', node: /\.ProseMirror\[contenteditable="false"\]$/ },
  { rule: 'label-content-name-mismatch', node: /^button\[aria-label="Numbered list"\]$/ },
];

const isKnownShared = (rule: string, target: string) => KNOWN_SHARED.some((k) => k.rule === rule && k.node.test(target));

async function auditPanel(page: Page, name: string): Promise<void> {
  const results = await new AxeBuilder({ page }).include('[data-testid="task-panel"]').withTags(WCAG_TAGS).analyze();
  await test.info().attach(`axe-${name}.json`, { body: JSON.stringify(results.violations, null, 2), contentType: 'application/json' });
  const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  const failures: string[] = [];
  const known: string[] = [];
  for (const v of serious) {
    for (const node of v.nodes) {
      const target = node.target.join(' ');
      (isKnownShared(v.id, target) ? known : failures).push(`${v.impact} ${v.id}: ${v.help} -> ${target}`);
    }
  }
  if (known.length > 0) test.info().annotations.push({ type: 'known shared-component a11y issue', description: known.join('\n') });
  expect(failures, `serious a11y violations in ${name}`).toEqual([]);
}

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`task panel a11y (${colorScheme})`, () => {
    test.use({ colorScheme });

    test('the panel (desktop) / sheet (375 px) has no serious axe violations', async ({ page, admin }, info) => {
      const role = await createRole(admin, 'a11y', ['tasks.view', 'tasks.create', 'tasks.assign']);
      const author = await createActor(admin, 'a11y', { roleId: role.id });
      const space = await admin.post<{ id: string; statuses: Array<{ id: string; category: string }> }>('/spaces', {
        name: `E2E a11y space ${token()}`,
        general: true,
      });
      const session = await sessionOf(author);
      const title = `E2E a11y task ${token()}`;
      const task = await session.post<{ id: string }>('/tasks', {
        spaceId: space.id,
        title,
        // Overdue, so the red deadline highlight is audited too.
        deadline: kyivDate(-2),
        description: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Fake description for the audit.' }] }] },
        labels: ['fake-label'],
      });
      await session.post(`/tasks/${task.id}/assignees`, { userId: author.id });
      const subtask = await session.post<{ id: string }>(`/tasks/${task.id}/subtasks`, { title: `E2E a11y subtask ${token()}` });
      const done = space.statuses.find((s) => s.category === 'DONE');
      await session.post(`/tasks/${subtask.id}/status`, { statusId: done?.id });
      await session.post(`/tasks/${task.id}/artifacts`, { type: 'LINK', url: 'https://example.org/fake-a11y', title: 'Fake link' });
      await session.post('/comments', { targetType: 'TASK', targetId: task.id, body: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Fake comment' }] }] } });

      await signIn(page, author);
      await page.goto(`/tasks/${task.id}`);
      await expect(page.locator('html')).toHaveAttribute('data-mantine-color-scheme', colorScheme);
      const mobile = info.project.name === 'mobile-375';
      const panel = mobile ? page.getByRole('dialog', { name: title }) : page.getByRole('region', { name: title });
      await expect(panel).toBeVisible();
      await expect(panel.getByTestId('suggest-close')).toBeVisible();
      await expect(panel.getByText('Fake comment')).toBeVisible();
      // Let the sheet finish sliding in (its transition would otherwise skew the colour checks).
      await page.waitForTimeout(400);

      await auditPanel(page, `${mobile ? 'sheet' : 'panel'}-${colorScheme}`);

      // The inline forms too: new subtask and add link.
      await panel.getByRole('button', { name: 'New subtask' }).click();
      await panel.getByRole('button', { name: 'Add link' }).click();
      await expect(panel.getByTestId('subtask-form')).toBeVisible();
      await expect(panel.getByTestId('artifact-link-form')).toBeVisible();
      await auditPanel(page, `${mobile ? 'sheet' : 'panel'}-forms-${colorScheme}`);
    });
  });
}
