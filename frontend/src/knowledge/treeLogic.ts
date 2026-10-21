import type { TreeNode } from './knowledgeApi';

/** Ids on the path from the root to `targetId` (inclusive), or `[]` when it is not in the tree. */
export function pathTo(nodes: readonly TreeNode[], targetId: string): string[] {
  for (const n of nodes) {
    if (n.id === targetId) return [n.id];
    const rest = pathTo(n.children, targetId);
    if (rest.length > 0) return [n.id, ...rest];
  }
  return [];
}

/** Ids of a node and all its descendants. */
export function subtreeIds(nodes: readonly TreeNode[], rootId: string): Set<string> {
  const ids = new Set<string>();
  const collect = (n: TreeNode) => {
    ids.add(n.id);
    n.children.forEach(collect);
  };
  const find = (list: readonly TreeNode[]): TreeNode | undefined => {
    for (const n of list) {
      if (n.id === rootId) return n;
      const hit = find(n.children);
      if (hit) return hit;
    }
    return undefined;
  };
  const root = find(nodes);
  if (root) collect(root);
  return ids;
}

export interface ParentOption {
  value: string;
  label: string;
}

/** Possible new parents for a page: every page except itself and its own sub-pages, indented by depth. */
export function parentOptions(nodes: readonly TreeNode[], movingId: string): ParentOption[] {
  const excluded = subtreeIds(nodes, movingId);
  const out: ParentOption[] = [];
  const walk = (list: readonly TreeNode[], depth: number) => {
    for (const n of list) {
      if (excluded.has(n.id)) continue;
      out.push({ value: n.id, label: `${'  '.repeat(depth)}${n.title}` });
      walk(n.children, depth + 1);
    }
  };
  walk(nodes, 0);
  return out;
}
