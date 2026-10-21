import type { MemberSummary } from '../../members/memberApi';
import type { TaskSummary } from '../../tasks/tasksApi';
import type { TreeNode } from '../knowledgeApi';
import type { MentionEntity } from './nodes';

export interface MentionCandidate {
  id: string;
  label: string;
  entityType: MentionEntity;
}

/** Flat list of the pages of a tree, depth first (the tree already omits what the caller may not see). */
export function flattenTree(nodes: readonly TreeNode[]): { id: string; title: string }[] {
  return nodes.flatMap((n) => [{ id: n.id, title: n.title }, ...flattenTree(n.children)]);
}

export const memberLabel = (m: Pick<MemberSummary, 'lastName' | 'firstName'>) => `${m.lastName} ${m.firstName}`.trim();

const matches = (text: string, q: string) => q === '' || text.toLowerCase().includes(q);

/**
 * Mention suggestions for a search text: people (name, telegram), articles (title) and tasks (title),
 * at most `perKind` of each, in that order. Archived people are not offered.
 */
export function buildMentionCandidates(
  source: {
    members: readonly MemberSummary[];
    articles: readonly { id: string; title: string }[];
    tasks: readonly Pick<TaskSummary, 'id' | 'title'>[];
  },
  query: string,
  perKind = 5,
): MentionCandidate[] {
  const q = query.trim().toLowerCase();
  const people = source.members
    .filter((m) => !m.archived && matches(`${m.lastName} ${m.firstName} ${m.middleName} ${m.telegram}`, q))
    .slice(0, perKind)
    .map((m): MentionCandidate => ({ id: m.id, label: memberLabel(m), entityType: 'person' }));
  const articles = source.articles
    .filter((a) => matches(a.title, q))
    .slice(0, perKind)
    .map((a): MentionCandidate => ({ id: a.id, label: a.title, entityType: 'article' }));
  const tasks = source.tasks
    .filter((t) => matches(t.title, q))
    .slice(0, perKind)
    .map((t): MentionCandidate => ({ id: t.id, label: t.title, entityType: 'task' }));
  return [...people, ...articles, ...tasks];
}
