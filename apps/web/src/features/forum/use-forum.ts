'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { forumApi, type DiscussionToggle } from '@/lib/api/endpoints/forum';
import { getNextCursor } from '@/lib/api/pagination';
import type { BlockDoc } from '@/lib/api/schemas/blockdoc';
import type { Post } from '@/lib/api/schemas/forum';
import { queryKeys } from '../query-keys';

/** FR-FORUM-01: tree depth ≤ 3, deeper replies attach flat to the level-3 parent. */
export const MAX_THREAD_DEPTH = 3;

export function replyParentId(
  post: Post,
  depth: number,
  parentOf: (id: string) => string | null,
): string {
  if (depth < MAX_THREAD_DEPTH) return post.id;
  return parentOf(post.id) ?? post.id;
}

export function useDiscussions(itemId: string) {
  return useInfiniteQuery({
    queryKey: queryKeys.discussions(itemId),
    queryFn: ({ pageParam }) => forumApi.discussions(itemId, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

export function useThread(discussionId: string) {
  return useQuery({
    queryKey: queryKeys.thread(discussionId),
    queryFn: () => forumApi.thread(discussionId),
  });
}

export function useForumMutations(itemId: string, discussionId?: string) {
  const queryClient = useQueryClient();
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.discussions(itemId) });
    if (discussionId)
      void queryClient.invalidateQueries({ queryKey: queryKeys.thread(discussionId) });
  };
  return {
    createDiscussion: useMutation({
      mutationFn: (input: { title: string; body: BlockDoc }) =>
        forumApi.createDiscussion(itemId, input),
      onSuccess: invalidate,
    }),
    reply: useMutation({
      mutationFn: (input: { parentId: string | null; body: BlockDoc }) =>
        forumApi.reply(discussionId ?? '', input),
      onSuccess: invalidate,
    }),
    edit: useMutation({
      mutationFn: ({ id, body }: { id: string; body: BlockDoc }) => forumApi.editPost(id, body),
      onSuccess: invalidate,
    }),
    remove: useMutation({
      mutationFn: (id: string) => forumApi.deletePost(id),
      onSuccess: invalidate,
    }),
    toggle: useMutation({
      mutationFn: ({ toggle, enabled }: { toggle: DiscussionToggle; enabled: boolean }) =>
        forumApi.toggle(discussionId ?? '', toggle, enabled),
      onSuccess: invalidate,
    }),
    markRead: useMutation({
      mutationFn: () => forumApi.markRead(discussionId ?? ''),
      onSuccess: invalidate,
      meta: { skipErrorToast: true },
    }),
  };
}
