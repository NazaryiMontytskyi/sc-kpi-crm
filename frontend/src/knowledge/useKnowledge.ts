import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  archiveArticle,
  createArticle,
  getArticle,
  getArticleTree,
  getTags,
  listArticles,
  moveArticle,
  restoreArticle,
  updateArticle,
  type ArticleInput,
} from './knowledgeApi';

export const KB_KEY = ['knowledge'] as const;

export const useArticleTree = () => useQuery({ queryKey: [...KB_KEY, 'tree'], queryFn: getArticleTree });
export const useTags = () => useQuery({ queryKey: [...KB_KEY, 'tags'], queryFn: getTags });
export const useArticles = (tag: string | undefined, includeArchived: boolean) =>
  useQuery({ queryKey: [...KB_KEY, 'list', tag ?? null, includeArchived], queryFn: () => listArticles(tag, includeArchived) });
export const useArticle = (id: string | undefined) =>
  useQuery({ queryKey: [...KB_KEY, 'article', id], queryFn: () => getArticle(id ?? ''), enabled: !!id });

export function useArticleMutations() {
  const qc = useQueryClient();
  const onSuccess = () => qc.invalidateQueries({ queryKey: KB_KEY });
  return {
    create: useMutation({ mutationFn: (v: { input: ArticleInput; parentId: string | null }) => createArticle(v.input, v.parentId), onSuccess }),
    update: useMutation({
      mutationFn: (v: { id: string; expectedVersion: number; input: ArticleInput }) => updateArticle(v.id, v.expectedVersion, v.input),
      onSuccess,
    }),
    move: useMutation({ mutationFn: (v: { id: string; parentId: string | null }) => moveArticle(v.id, v.parentId), onSuccess }),
    archive: useMutation({ mutationFn: (id: string) => archiveArticle(id), onSuccess }),
    restore: useMutation({ mutationFn: (id: string) => restoreArticle(id), onSuccess }),
  };
}
