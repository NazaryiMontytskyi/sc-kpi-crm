import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type Schemas = components['schemas'];

export type ResolutionState = NonNullable<Schemas['ResolutionResponse']['state']>;

export const RESOLUTION_STATES: readonly ResolutionState[] = ['NEW', 'IN_PROGRESS', 'SUBMITTED', 'REWORK', 'ACCEPTED', 'REDIRECTED', 'CANCELED'];

/** The generated types mark every field optional; this is the shape the UI relies on. */
export interface Resolution {
  id: string;
  taskId: string;
  authorId: string;
  executorId: string;
  coExecutorIds: string[];
  instruction: string;
  dueDate: string;
  parentResolutionId: string | null;
  state: ResolutionState;
  overdue: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ResolutionHistoryEntry {
  actorId: string;
  from: ResolutionState | null;
  to: ResolutionState;
  comment: string;
  at: string;
}

export interface ResolutionDetail extends Resolution {
  completionNote: string;
  history: ResolutionHistoryEntry[];
}

/** A resolution of a redirect chain with its (non-archived) redirects nested below it. */
export interface ResolutionChainNode extends Resolution {
  children: ResolutionChainNode[];
}

export interface IssueInput {
  executorId: string;
  coExecutorIds: string[];
  instruction: string;
  dueDate: string;
}

export interface RedirectInput {
  executorId: string;
  instruction: string;
  dueDate: string;
}

export interface ResolutionPage {
  items: Resolution[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export const RESOLUTIONS_PAGE_SIZE = 20;

type SummaryDto = Schemas['ResolutionSummaryResponse'];

function toResolution(r: SummaryDto): Resolution {
  return {
    id: r.id ?? '',
    taskId: r.taskId ?? '',
    authorId: r.authorId ?? '',
    executorId: r.executorId ?? '',
    coExecutorIds: r.coExecutorIds ?? [],
    instruction: r.instruction ?? '',
    dueDate: r.dueDate ?? '',
    parentResolutionId: r.parentResolutionId ?? null,
    state: r.state ?? 'NEW',
    overdue: r.overdue === true,
    createdAt: r.createdAt ?? '',
    updatedAt: r.updatedAt ?? '',
  };
}

function toDetail(r: Schemas['ResolutionResponse']): ResolutionDetail {
  return {
    ...toResolution(r),
    completionNote: r.completionNote ?? '',
    history: (r.history ?? []).map((h) => ({
      actorId: h.actorId ?? '',
      from: h.from ?? null,
      to: h.to ?? 'NEW',
      comment: h.comment ?? '',
      at: h.at ?? '',
    })),
  };
}

export async function listTaskResolutions(taskId: string, page: number): Promise<ResolutionPage> {
  const { data, error, response } = await api.GET('/api/v1/tasks/{taskId}/resolutions', {
    params: { path: { taskId }, query: { page: { page, size: RESOLUTIONS_PAGE_SIZE, sort: 'createdAt,desc' } } },
  });
  if (!data) throw toProblemError(response, error);
  return {
    items: (data.content ?? []).map(toResolution),
    page: data.page ?? 0,
    totalPages: data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
  };
}

function toChainNode(n: Schemas['ResolutionChainNode']): ResolutionChainNode {
  return { ...toResolution(n), children: (n.children ?? []).map(toChainNode) };
}

/** The whole redirect chain of a resolution as a tree rooted at the first resolution. */
export async function getResolutionChain(id: string): Promise<ResolutionChainNode> {
  const { data, error, response } = await api.GET('/api/v1/resolutions/{id}/chain', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toChainNode(data);
}

export async function getResolution(id: string): Promise<ResolutionDetail> {
  const { data, error, response } = await api.GET('/api/v1/resolutions/{id}', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toDetail(data);
}

export async function issueResolution(taskId: string, input: IssueInput): Promise<ResolutionDetail> {
  const { data, error, response } = await api.POST('/api/v1/tasks/{taskId}/resolutions', { params: { path: { taskId } }, body: input });
  if (!data) throw toProblemError(response, error);
  return toDetail(data);
}

export type SimpleAction = 'start' | 'resume' | 'accept';

export async function simpleResolutionAction(id: string, action: SimpleAction): Promise<ResolutionDetail> {
  const path = { params: { path: { id } } };
  const { data, error, response } =
    action === 'start'
      ? await api.POST('/api/v1/resolutions/{id}/start', path)
      : action === 'resume'
        ? await api.POST('/api/v1/resolutions/{id}/resume', path)
        : await api.POST('/api/v1/resolutions/{id}/accept', path);
  if (!data) throw toProblemError(response, error);
  return toDetail(data);
}

export async function submitResolution(id: string, completionNote: string): Promise<ResolutionDetail> {
  const { data, error, response } = await api.POST('/api/v1/resolutions/{id}/submit', { params: { path: { id } }, body: { completionNote } });
  if (!data) throw toProblemError(response, error);
  return toDetail(data);
}

export async function reworkResolution(id: string, comment: string): Promise<ResolutionDetail> {
  const { data, error, response } = await api.POST('/api/v1/resolutions/{id}/rework', { params: { path: { id } }, body: { comment } });
  if (!data) throw toProblemError(response, error);
  return toDetail(data);
}

export async function cancelResolution(id: string, comment?: string): Promise<ResolutionDetail> {
  const { data, error, response } = await api.POST('/api/v1/resolutions/{id}/cancel', {
    params: { path: { id } },
    body: comment ? { comment } : {},
  });
  if (!data) throw toProblemError(response, error);
  return toDetail(data);
}

/** Redirects to a new executor; the result carries the soft warnings (e.g. RESOLUTION_CHILD_DUE_AFTER_PARENT). */
export async function redirectResolution(id: string, input: RedirectInput): Promise<{ child: ResolutionDetail; warnings: string[] }> {
  const { data, error, response } = await api.POST('/api/v1/resolutions/{id}/redirect', { params: { path: { id } }, body: input });
  if (!data?.child) throw toProblemError(response, error);
  return { child: toDetail(data.child), warnings: data.warnings ?? [] };
}
