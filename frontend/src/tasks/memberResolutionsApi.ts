import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';
import type { ResolutionState } from './resolutionsApi';

type Schemas = components['schemas'];

/** Which side of a resolution a person is on: executor / co-executor, or author. */
export type MemberResolutionRole = 'EXECUTOR' | 'AUTHOR';

/** A resolution of a person's list with the title of its task. */
export interface MemberResolution {
  id: string;
  taskId: string;
  taskTitle: string;
  authorId: string;
  executorId: string;
  coExecutorIds: string[];
  instruction: string;
  dueDate: string;
  state: ResolutionState;
  overdue: boolean;
}

export interface MemberResolutionPage {
  items: MemberResolution[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export interface MemberResolutionCounts {
  active: number;
  overdue: number;
  accepted: number;
}

export interface MemberResolutionQuery {
  role: MemberResolutionRole;
  /** Only NEW, IN_PROGRESS, SUBMITTED and REWORK. */
  active?: boolean;
  state?: ResolutionState;
  page: number;
}

export const MEMBER_RESOLUTIONS_PAGE_SIZE = 20;

function toItem(r: Schemas['MemberResolutionResponse']): MemberResolution {
  return {
    id: r.id ?? '',
    taskId: r.taskId ?? '',
    taskTitle: r.taskTitle ?? '',
    authorId: r.authorId ?? '',
    executorId: r.executorId ?? '',
    coExecutorIds: r.coExecutorIds ?? [],
    instruction: r.instruction ?? '',
    dueDate: r.dueDate ?? '',
    state: r.state ?? 'NEW',
    overdue: r.overdue === true,
  };
}

export async function listMemberResolutions(memberId: string, q: MemberResolutionQuery): Promise<MemberResolutionPage> {
  const { data, error, response } = await api.GET('/api/v1/members/{id}/resolutions', {
    params: {
      path: { id: memberId },
      query: {
        page: q.page, size: MEMBER_RESOLUTIONS_PAGE_SIZE,
        role: q.role,
        active: q.active ?? false,
        state: q.state,
      },
    },
  });
  if (!data) throw toProblemError(response, error);
  return {
    items: (data.content ?? []).map(toItem),
    page: data.page ?? 0,
    totalPages: data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
  };
}

export async function getMemberResolutionCounts(memberId: string): Promise<MemberResolutionCounts> {
  const { data, error, response } = await api.GET('/api/v1/members/{id}/resolutions/counts', { params: { path: { id: memberId } } });
  if (!data) throw toProblemError(response, error);
  return { active: data.active ?? 0, overdue: data.overdue ?? 0, accepted: data.accepted ?? 0 };
}
