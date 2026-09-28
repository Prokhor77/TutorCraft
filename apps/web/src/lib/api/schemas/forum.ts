import { z } from 'zod';
import { blockDocSchema } from './blockdoc';
import { idSchema, instantSchema, nullableInstant } from './common';

export const discussionSchema = z.object({
  id: idSchema,
  title: z.string(),
  authorId: idSchema,
  authorName: z.string(),
  pinned: z.boolean(),
  locked: z.boolean(),
  subscribed: z.boolean(),
  replyCount: z.number(),
  unreadCount: z.number(),
  lastPostAt: instantSchema,
  createdAt: instantSchema,
});
export type Discussion = z.infer<typeof discussionSchema>;

export type Post = {
  id: string;
  parentId: string | null;
  authorId: string;
  authorName: string;
  body: z.infer<typeof blockDocSchema>;
  createdAt: string;
  editedAt: string | null;
  canEdit: boolean;
  canDelete: boolean;
  hidden: boolean;
  children: Post[];
};
export const postSchema: z.ZodType<Post, z.ZodTypeDef, unknown> = z.lazy(() =>
  z.object({
    id: idSchema,
    parentId: idSchema.nullable(),
    authorId: idSchema,
    authorName: z.string(),
    body: blockDocSchema,
    createdAt: instantSchema,
    editedAt: nullableInstant,
    canEdit: z.boolean(),
    canDelete: z.boolean(),
    hidden: z.boolean(),
    children: z.array(postSchema),
  }),
);

export const discussionThreadSchema = z.object({
  discussion: discussionSchema,
  posts: z.array(postSchema),
});
export type DiscussionThread = z.infer<typeof discussionThreadSchema>;
