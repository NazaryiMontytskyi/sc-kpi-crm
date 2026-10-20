import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  archiveComment,
  createComment,
  editComment,
  listComments,
  type CommentInput,
  type CommentTargetType,
} from './commentsApi';

export const COMMENTS_KEY = ['comments'] as const;

/** The comment thread of a record, oldest first, loaded page by page ("show more"). */
export function useComments(targetType: CommentTargetType, targetId: string) {
  return useInfiniteQuery({
    queryKey: [...COMMENTS_KEY, targetType, targetId],
    queryFn: ({ pageParam }) => listComments(targetType, targetId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}

export function useCommentMutations(targetType: CommentTargetType, targetId: string) {
  const qc = useQueryClient();
  const onSuccess = () => qc.invalidateQueries({ queryKey: [...COMMENTS_KEY, targetType, targetId] });
  return {
    create: useMutation({ mutationFn: (input: CommentInput) => createComment(targetType, targetId, input), onSuccess }),
    edit: useMutation({ mutationFn: (v: { id: string; input: CommentInput }) => editComment(v.id, v.input), onSuccess }),
    archive: useMutation({ mutationFn: (id: string) => archiveComment(id), onSuccess }),
  };
}
