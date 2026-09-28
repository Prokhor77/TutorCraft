import { http } from '../http';
import type { BlockDoc } from '../schemas/blockdoc';
import { pageSchema } from '../schemas/common';
import { discussionSchema, discussionThreadSchema, postSchema } from '../schemas/forum';

export type DiscussionToggle = 'pin' | 'lock' | 'subscribe';

export const forumApi = {
  discussions: (itemId: string, cursor?: string | null) =>
    http.request(`/items/${itemId}/discussions`, {
      query: { cursor },
      schema: pageSchema(discussionSchema),
    }),
  createDiscussion: (itemId: string, body: { title: string; body: BlockDoc }) =>
    http.request(`/items/${itemId}/discussions`, {
      method: 'POST',
      body,
      schema: discussionSchema,
    }),
  thread: (id: string) => http.request(`/discussions/${id}`, { schema: discussionThreadSchema }),
  reply: (discussionId: string, body: { parentId: string | null; body: BlockDoc }) =>
    http.request(`/discussions/${discussionId}/posts`, {
      method: 'POST',
      body,
      schema: postSchema,
    }),
  editPost: (id: string, body: BlockDoc) =>
    http.request(`/posts/${id}`, { method: 'PATCH', body: { body } }),
  deletePost: (id: string) => http.request(`/posts/${id}`, { method: 'DELETE' }),
  toggle: (discussionId: string, toggle: DiscussionToggle, enabled: boolean) =>
    http.request(`/discussions/${discussionId}/${toggle}`, { method: enabled ? 'POST' : 'DELETE' }),
  markRead: (discussionId: string) =>
    http.request(`/discussions/${discussionId}/read`, { method: 'POST' }),
};
