import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type Schemas = components['schemas'];

/** An open task (or subtask) a member is an active assignee of, as listed on the member profile. */
export interface MemberTask {
  id: string;
  parentId: string | null;
  title: string;
  statusName: string;
  deadline: string | null;
  /** Deadline before today in Europe/Kyiv (computed by the backend). */
  overdue: boolean;
}

export interface MemberTaskPage {
  items: MemberTask[];
  page: number;
  totalPages: number;
  totalElements: number;
}

/** Counters over the non-archived tasks a member is an active assignee of. */
export interface MemberTaskCounts {
  active: number;
  overdue: number;
  done: number;
}

export const MEMBER_TASKS_PAGE_SIZE = 20;

function toItem(t: Schemas['MemberTaskResponse']): MemberTask {
  return {
    id: t.id,
    parentId: t.parentId ?? null,
    title: t.title,
    statusName: t.statusName,
    deadline: t.deadline ?? null,
    overdue: t.overdue === true,
  };
}

/** Open tasks of a member, earliest deadline first and no deadline last (the order is fixed by the backend). */
export async function listMemberTasks(memberId: string, page: number): Promise<MemberTaskPage> {
  const { data, error, response } = await api.GET('/api/v1/members/{id}/tasks', {
    params: { path: { id: memberId }, query: { page, size: MEMBER_TASKS_PAGE_SIZE } },
  });
  if (!data) throw toProblemError(response, error);
  return {
    items: (data.content ?? []).map(toItem),
    page: data.page ?? 0,
    totalPages: data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
  };
}

export async function getMemberTaskCounts(memberId: string): Promise<MemberTaskCounts> {
  const { data, error, response } = await api.GET('/api/v1/members/{id}/tasks/counts', { params: { path: { id: memberId } } });
  if (!data) throw toProblemError(response, error);
  return { active: data.active ?? 0, overdue: data.overdue ?? 0, done: data.done ?? 0 };
}
