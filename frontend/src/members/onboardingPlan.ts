/** One API call of the onboarding wizard (CONTEXT.md 5.1 steps 3-4): join a unit or receive a role. */
export type OnboardingKind = 'department' | 'role';
export type OnboardingStatus = 'todo' | 'done' | 'failed';

export interface OnboardingTask {
  key: string;
  kind: OnboardingKind;
  /** Org unit id or role id. */
  targetId: string;
  /** Display name of the unit or role. */
  label: string;
  status: OnboardingStatus;
  /** i18n key of the failure reason when `status` is `failed`. */
  errorKey?: string;
}

export interface OnboardingSelection {
  departments: readonly { id: string; name: string }[];
  role: { id: string; name: string } | null;
}

/** Builds the task list; a step the user may not perform is skipped, whatever was selected. */
export function buildTasks(
  selection: OnboardingSelection,
  allowed: { departments: boolean; role: boolean },
): OnboardingTask[] {
  const tasks: OnboardingTask[] = [];
  if (allowed.departments) {
    for (const d of selection.departments) {
      tasks.push({ key: `department:${d.id}`, kind: 'department', targetId: d.id, label: d.name, status: 'todo' });
    }
  }
  if (allowed.role && selection.role) {
    tasks.push({
      key: `role:${selection.role.id}`,
      kind: 'role',
      targetId: selection.role.id,
      label: selection.role.name,
      status: 'todo',
    });
  }
  return tasks;
}

/** Tasks that still have to be done (never attempted or failed). */
export function remainingTasks(tasks: readonly OnboardingTask[]): OnboardingTask[] {
  return tasks.filter((t) => t.status !== 'done');
}

/**
 * Runs every task that is not done yet, one after another. A failure is recorded on its task and does not undo
 * or block anything: tasks already done stay done, the rest still run. `onUpdate` receives the full list after
 * each task so the UI can show progress. Returns the final list.
 */
export async function runTasks(
  tasks: readonly OnboardingTask[],
  execute: (task: OnboardingTask) => Promise<void>,
  errorKey: (error: unknown) => string,
  onUpdate: (tasks: OnboardingTask[]) => void,
): Promise<OnboardingTask[]> {
  let current = tasks.map((t) => ({ ...t }));
  for (let i = 0; i < current.length; i++) {
    const task = current[i];
    if (task.status === 'done') continue;
    let next: OnboardingTask;
    try {
      await execute(task);
      next = { ...task, status: 'done', errorKey: undefined };
    } catch (error) {
      next = { ...task, status: 'failed', errorKey: errorKey(error) };
    }
    current = current.map((t, idx) => (idx === i ? next : t));
    onUpdate(current);
  }
  return current;
}
