import { api, type components } from '../api/client';
import { toProblemError } from '../api/problem';

type Schemas = components['schemas'];

export type TaskPriority = NonNullable<Schemas['TaskResponse']['priority']>;
export type StatusCategory = NonNullable<Schemas['SpaceStatusResponse']['category']>;
export type CustomFieldType = NonNullable<Schemas['CustomFieldResponse']['type']>;
/** TipTap/ProseMirror JSON document. */
export type RichDoc = Record<string, unknown>;
export type CustomFieldValues = Record<string, unknown>;

export const PRIORITIES: readonly TaskPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
export const STATUS_CATEGORIES: readonly StatusCategory[] = ['TODO', 'IN_PROGRESS', 'DONE', 'CANCELED'];
export const FIELD_TYPES: readonly CustomFieldType[] = ['TEXT', 'NUMBER', 'DATE', 'SELECT', 'PERSON'];

export interface SpaceSummary {
  id: string;
  name: string;
  orgUnitId: string | null;
  general: boolean;
  archived: boolean;
}

export interface SpaceStatus {
  id: string;
  name: string;
  category: StatusCategory;
  position: number;
  archived: boolean;
}

export interface CustomField {
  id: string;
  name: string;
  type: CustomFieldType;
  required: boolean;
  options: string[];
  archived: boolean;
}

export interface Space extends SpaceSummary {
  statuses: SpaceStatus[];
  customFields: CustomField[];
}

export interface TaskSummary {
  id: string;
  spaceId: string;
  title: string;
  statusId: string;
  priority: TaskPriority;
  dueDate: string | null;
  labels: string[];
  attachmentCount: number;
  authorId: string;
  archived: boolean;
  createdAt: string;
}

export interface Task extends TaskSummary {
  description: RichDoc | null;
  customFieldValues: CustomFieldValues;
  relatedTaskIds: string[];
  /** Ids of attached files; PUT is a full replacement, so they are sent back unchanged on edit. */
  attachmentIds: string[];
  /** Computed by the backend: every non-canceled resolution is accepted and the task is not yet done. */
  suggestDone: boolean;
}

export interface TaskInput {
  title: string;
  description?: RichDoc;
  priority: TaskPriority;
  dueDate?: string;
  labels: string[];
  customFieldValues: CustomFieldValues;
}

export interface CreateTaskInput extends TaskInput {
  spaceId: string;
  statusId?: string;
}

export interface SpaceInput {
  name: string;
  orgUnitId?: string;
  general: boolean;
}

export interface StatusInput {
  name: string;
  category: StatusCategory;
}

export interface CustomFieldInput {
  name: string;
  required: boolean;
  options?: string[];
}

export interface CreateCustomFieldInput extends CustomFieldInput {
  type: CustomFieldType;
}

export type TaskSortField = 'createdAt' | 'updatedAt' | 'dueDate' | 'title';
export type SortDirection = 'asc' | 'desc';

export interface TaskQuery {
  spaceId?: string;
  statusId?: string;
  labels: string[];
  dueFrom?: string;
  dueTo?: string;
  includeArchived: boolean;
  sortBy: TaskSortField;
  direction: SortDirection;
  page: number;
  size: number;
}

export interface Paged<T> {
  items: T[];
  page: number;
  totalPages: number;
  totalElements: number;
}

/** Backend maximum page size. */
const PAGE_SIZE = 100;
/** Safety cap so a misbehaving server cannot make the client loop forever. */
const MAX_PAGES = 50;

function toSpaceSummary(s: Schemas['SpaceSummaryResponse']): SpaceSummary {
  return {
    id: s.id ?? '',
    name: s.name ?? '',
    orgUnitId: s.orgUnitId ?? null,
    general: s.general === true,
    archived: s.archived === true,
  };
}

function toStatus(s: Schemas['SpaceStatusResponse']): SpaceStatus {
  return {
    id: s.id ?? '',
    name: s.name ?? '',
    category: s.category ?? 'TODO',
    position: s.position ?? 0,
    archived: s.archived === true,
  };
}

function toField(f: Schemas['CustomFieldResponse']): CustomField {
  return {
    id: f.id ?? '',
    name: f.name ?? '',
    type: f.type ?? 'TEXT',
    required: f.required === true,
    options: f.options ?? [],
    archived: f.archived === true,
  };
}

function toSpace(s: Schemas['SpaceResponse']): Space {
  return {
    ...toSpaceSummary(s),
    statuses: (s.statuses ?? []).map(toStatus).sort((a, b) => a.position - b.position),
    customFields: (s.customFields ?? []).map(toField),
  };
}

function toTaskSummary(t: Schemas['TaskSummaryResponse']): TaskSummary {
  return {
    id: t.id ?? '',
    spaceId: t.spaceId ?? '',
    title: t.title ?? '',
    statusId: t.statusId ?? '',
    priority: t.priority ?? 'NORMAL',
    dueDate: t.dueDate ?? null,
    labels: t.labels ?? [],
    attachmentCount: t.attachmentCount ?? 0,
    authorId: t.authorId ?? '',
    archived: t.archived === true,
    createdAt: t.createdAt ?? '',
  };
}

function toTask(t: Schemas['TaskResponse']): Task {
  return {
    ...toTaskSummary({ ...t, attachmentCount: t.attachments?.length ?? 0 }),
    description: t.description ?? null,
    customFieldValues: t.customFieldValues ?? {},
    relatedTaskIds: t.relatedTaskIds ?? [],
    attachmentIds: (t.attachments ?? []).flatMap((a) => (a.id ? [a.id] : [])),
    suggestDone: t.suggestDone === true,
  };
}

// ---- spaces ----------------------------------------------------------------------------------

export async function listSpaces(includeArchived: boolean): Promise<SpaceSummary[]> {
  const all: SpaceSummary[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    const { data, error, response } = await api.GET('/api/v1/spaces', {
      params: { query: { page: { page, size: PAGE_SIZE, sort: 'name' }, includeArchived } },
    });
    if (!data) throw toProblemError(response, error);
    all.push(...(data.content ?? []).map(toSpaceSummary));
    if (data.last !== false) break;
  }
  return all;
}

export async function getSpace(id: string): Promise<Space> {
  const { data, error, response } = await api.GET('/api/v1/spaces/{id}', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toSpace(data);
}

export async function createSpace(input: SpaceInput): Promise<Space> {
  const { data, error, response } = await api.POST('/api/v1/spaces', { body: input });
  if (!data) throw toProblemError(response, error);
  return toSpace(data);
}

export async function updateSpace(id: string, input: SpaceInput): Promise<Space> {
  const { data, error, response } = await api.PUT('/api/v1/spaces/{id}', { params: { path: { id } }, body: input });
  if (!data) throw toProblemError(response, error);
  return toSpace(data);
}

export async function archiveSpace(id: string): Promise<Space> {
  const { data, error, response } = await api.POST('/api/v1/spaces/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toSpace(data);
}

export async function restoreSpace(id: string): Promise<Space> {
  const { data, error, response } = await api.POST('/api/v1/spaces/{id}/restore', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toSpace(data);
}

// ---- statuses --------------------------------------------------------------------------------

export async function addStatus(spaceId: string, input: StatusInput): Promise<SpaceStatus> {
  const { data, error, response } = await api.POST('/api/v1/spaces/{id}/statuses', {
    params: { path: { id: spaceId } },
    body: input,
  });
  if (!data) throw toProblemError(response, error);
  return toStatus(data);
}

export async function updateStatus(spaceId: string, statusId: string, input: StatusInput): Promise<SpaceStatus> {
  const { data, error, response } = await api.PUT('/api/v1/spaces/{id}/statuses/{statusId}', {
    params: { path: { id: spaceId, statusId } },
    body: input,
  });
  if (!data) throw toProblemError(response, error);
  return toStatus(data);
}

export async function reorderStatuses(spaceId: string, statusIds: string[]): Promise<void> {
  const { error, response } = await api.PUT('/api/v1/spaces/{id}/statuses/order', {
    params: { path: { id: spaceId } },
    body: { statusIds },
  });
  if (!response.ok) throw toProblemError(response, error);
}

export async function archiveStatus(spaceId: string, statusId: string, replacementStatusId?: string): Promise<void> {
  const { error, response } = await api.POST('/api/v1/spaces/{id}/statuses/{statusId}/archive', {
    params: { path: { id: spaceId, statusId } },
    body: { replacementStatusId },
  });
  if (!response.ok) throw toProblemError(response, error);
}

// ---- custom fields ---------------------------------------------------------------------------

export async function addCustomField(spaceId: string, input: CreateCustomFieldInput): Promise<CustomField> {
  const { data, error, response } = await api.POST('/api/v1/spaces/{id}/custom-fields', {
    params: { path: { id: spaceId } },
    body: input,
  });
  if (!data) throw toProblemError(response, error);
  return toField(data);
}

export async function updateCustomField(spaceId: string, fieldId: string, input: CustomFieldInput): Promise<CustomField> {
  const { data, error, response } = await api.PUT('/api/v1/spaces/{id}/custom-fields/{fieldId}', {
    params: { path: { id: spaceId, fieldId } },
    body: input,
  });
  if (!data) throw toProblemError(response, error);
  return toField(data);
}

export async function archiveCustomField(spaceId: string, fieldId: string): Promise<void> {
  const { error, response } = await api.POST('/api/v1/spaces/{id}/custom-fields/{fieldId}/archive', {
    params: { path: { id: spaceId, fieldId } },
  });
  if (!response.ok) throw toProblemError(response, error);
}

// ---- tasks -----------------------------------------------------------------------------------

export async function listTasks(q: TaskQuery): Promise<Paged<TaskSummary>> {
  const { data, error, response } = await api.GET('/api/v1/tasks', {
    params: {
      query: {
        page: { page: q.page, size: q.size, sort: `${q.sortBy},${q.direction}` },
        spaceId: q.spaceId,
        statusId: q.statusId,
        labels: q.labels.length > 0 ? q.labels : undefined,
        dueFrom: q.dueFrom,
        dueTo: q.dueTo,
        includeArchived: q.includeArchived || undefined,
      },
    },
  });
  if (!data) throw toProblemError(response, error);
  return {
    items: (data.content ?? []).map(toTaskSummary),
    page: data.page ?? 0,
    totalPages: data.totalPages ?? 0,
    totalElements: data.totalElements ?? 0,
  };
}

export async function getTask(id: string): Promise<Task> {
  const { data, error, response } = await api.GET('/api/v1/tasks/{id}', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toTask(data);
}

export async function createTask(input: CreateTaskInput): Promise<Task> {
  const { data, error, response } = await api.POST('/api/v1/tasks', { body: input });
  if (!data) throw toProblemError(response, error);
  return toTask(data);
}

export async function updateTask(id: string, input: TaskInput, relatedTaskIds: string[], attachmentIds: string[]): Promise<Task> {
  // PUT replaces all editable fields, so existing related links and attachments are sent back unchanged.
  const { data, error, response } = await api.PUT('/api/v1/tasks/{id}', {
    params: { path: { id } },
    body: { ...input, relatedTaskIds, attachmentIds },
  });
  if (!data) throw toProblemError(response, error);
  return toTask(data);
}

export async function changeTaskStatus(id: string, statusId: string): Promise<Task> {
  const { data, error, response } = await api.POST('/api/v1/tasks/{id}/status', {
    params: { path: { id } },
    body: { statusId },
  });
  if (!data) throw toProblemError(response, error);
  return toTask(data);
}

export async function archiveTask(id: string): Promise<Task> {
  const { data, error, response } = await api.POST('/api/v1/tasks/{id}/archive', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toTask(data);
}

export async function restoreTask(id: string): Promise<Task> {
  const { data, error, response } = await api.POST('/api/v1/tasks/{id}/restore', { params: { path: { id } } });
  if (!data) throw toProblemError(response, error);
  return toTask(data);
}
