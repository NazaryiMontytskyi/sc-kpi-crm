import { ApiSession } from './api';
import { token } from './data';

/** A minimal rich-text document (TipTap JSON) with a single paragraph. */
export const doc = (text: string) => ({ type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] });

/** Signs in as `who` through the API and returns the session. */
export async function sessionOf(who: { login: string; password: string }): Promise<ApiSession> {
  const session = new ApiSession();
  await session.login(who.login, who.password);
  return session;
}

/** Creates a general space (as Admin) and a task authored by `author`; returns the task id. */
export async function createTask(admin: ApiSession, author: { login: string; password: string }, title: string): Promise<string> {
  const space = await admin.post<{ id: string }>('/spaces', { name: `E2E space ${token()}`, general: true });
  const task = await (await sessionOf(author)).post<{ id: string }>('/tasks', { spaceId: space.id, title });
  return task.id;
}
