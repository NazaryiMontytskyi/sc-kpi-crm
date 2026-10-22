import type { Page } from '@playwright/test';
import { createActor, createRole, kyivDate, token } from '../support/data';
import { expect, pickOption, signIn, test } from '../support/fixtures';
import { createTask } from '../support/scenarios';

const card = (page: Page, instruction: string) => page.getByTestId('resolution').filter({ hasText: instruction });

async function openResolutions(page: Page, taskId: string): Promise<void> {
  await page.goto(`/tasks/${taskId}?tab=resolutions`);
  await expect(page.getByRole('tab', { name: /Resolutions/, selected: true })).toBeVisible();
}

test('resolution: issue, redirect, submit, rework, accept', async ({ page, admin, openAs }) => {
  const issuerRole = await createRole(admin, 'issuer', ['tasks.view', 'tasks.create', 'resolutions.create']);
  const executorRole = await createRole(admin, 'executor', ['tasks.view', 'resolutions.redirect']);
  const assigneeRole = await createRole(admin, 'assignee', ['tasks.view']);
  const author = await createActor(admin, 'chair', { roleId: issuerRole.id });
  const executor = await createActor(admin, 'exec', { roleId: executorRole.id });
  const assignee = await createActor(admin, 'assign', { roleId: assigneeRole.id });
  const taskId = await createTask(admin, author, `E2E task ${token()}`);

  const issued = `Prepare the fake agenda ${token()}`;
  const redirected = `Collect the fake figures ${token()}`;

  // 1. The author issues a resolution to the executor.
  await signIn(page, author);
  await openResolutions(page, taskId);
  await page.getByRole('button', { name: 'Issue resolution' }).click();
  const issueDialog = page.getByRole('dialog', { name: 'New resolution' });
  await pickOption(page, /^Executor/, executor.fullName, executor.lastName, issueDialog);
  await issueDialog.getByLabel(/^Instruction/).fill(issued);
  await issueDialog.getByLabel(/^Due date/).fill(kyivDate(3));
  await issueDialog.getByRole('button', { name: 'Issue', exact: true }).click();
  await expect(card(page, issued)).toContainText('New');

  // 2. The executor starts it and redirects it to the assignee.
  const executorPage = await openAs(executor);
  await openResolutions(executorPage, taskId);
  await card(executorPage, issued).getByRole('button', { name: 'Start' }).click();
  await expect(card(executorPage, issued)).toContainText('In progress');
  await card(executorPage, issued).getByRole('button', { name: 'Redirect' }).click();
  const redirectDialog = executorPage.getByRole('dialog', { name: 'Redirect resolution' });
  await pickOption(executorPage, /^Executor/, assignee.fullName, assignee.lastName, redirectDialog);
  await redirectDialog.getByLabel(/^Instruction/).fill(redirected);
  await redirectDialog.getByLabel(/^Due date/).fill(kyivDate(2));
  await redirectDialog.getByRole('button', { name: 'Redirect', exact: true }).click();
  await expect(card(executorPage, issued)).toContainText('Redirected');
  await expect(card(executorPage, redirected)).toContainText('New');

  // 3. The assignee starts and submits the result.
  const assigneePage = await openAs(assignee);
  await openResolutions(assigneePage, taskId);
  await card(assigneePage, redirected).getByRole('button', { name: 'Start' }).click();
  await expect(card(assigneePage, redirected)).toContainText('In progress');
  await card(assigneePage, redirected).getByRole('button', { name: 'Submit result' }).click();
  await assigneePage.getByLabel(/^Completion note/).fill('First attempt, fake figures attached');
  await assigneePage.getByRole('dialog').getByRole('button', { name: 'Submit', exact: true }).click();
  await expect(card(assigneePage, redirected)).toContainText('Submitted');

  // 4. The redirecting executor (author of the new resolution) sends it back for rework.
  await openResolutions(executorPage, taskId);
  await card(executorPage, redirected).getByRole('button', { name: 'Send for rework' }).click();
  await executorPage.getByLabel(/^What needs rework/).fill('Please add the totals');
  await executorPage.getByRole('dialog').getByRole('button', { name: 'Send', exact: true }).click();
  await expect(card(executorPage, redirected)).toContainText('Rework');

  // 5. The assignee resumes and submits again.
  await openResolutions(assigneePage, taskId);
  await expect(card(assigneePage, redirected)).toContainText('Rework');
  await card(assigneePage, redirected).getByRole('button', { name: 'Resume' }).click();
  await expect(card(assigneePage, redirected)).toContainText('In progress');
  await card(assigneePage, redirected).getByRole('button', { name: 'Submit result' }).click();
  await assigneePage.getByLabel(/^Completion note/).fill('Totals added');
  await assigneePage.getByRole('dialog').getByRole('button', { name: 'Submit', exact: true }).click();
  await expect(card(assigneePage, redirected)).toContainText('Submitted');

  // 6. The executor accepts the result.
  await openResolutions(executorPage, taskId);
  await card(executorPage, redirected).getByRole('button', { name: 'Accept result' }).click();
  await expect(card(executorPage, redirected)).toContainText('Accepted');

  // The original author sees the whole chain: the first resolution stays "redirected", the new one is accepted.
  await openResolutions(page, taskId);
  await expect(card(page, issued)).toContainText('Redirected');
  await expect(card(page, redirected)).toContainText('Accepted');
});
