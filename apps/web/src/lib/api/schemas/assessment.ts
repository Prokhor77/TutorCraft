import { z } from 'zod';
import { blockDocSchema } from './blockdoc';
import { idSchema, instantSchema, nullableInstant, submissionStatusSchema } from './common';
import { fileMetaSchema } from './files';

export const submissionSchema = z.object({
  id: idSchema,
  itemId: idSchema,
  userId: idSchema,
  userName: z.string(),
  attemptNo: z.number(),
  status: submissionStatusSchema,
  text: blockDocSchema.nullable(),
  files: z.array(fileMetaSchema),
  submittedAt: nullableInstant,
  dueAt: nullableInstant,
  late: z.boolean(),
  grade: z
    .object({
      score: z.number().nullable(),
      maxScore: z.number(),
      published: z.boolean(),
      feedback: blockDocSchema.nullable(),
      feedbackFiles: z.array(fileMetaSchema),
      gradedAt: nullableInstant,
      graderName: z.string().nullable(),
    })
    .nullable(),
  history: z.array(
    z.object({
      attemptNo: z.number(),
      status: submissionStatusSchema,
      submittedAt: nullableInstant,
      score: z.number().nullable(),
    }),
  ),
  version: z.number(),
});
export type Submission = z.infer<typeof submissionSchema>;

export const submissionSummarySchema = z.object({
  id: idSchema,
  userId: idSchema,
  userName: z.string(),
  status: submissionStatusSchema,
  submittedAt: nullableInstant,
  late: z.boolean(),
  score: z.number().nullable(),
});
export type SubmissionSummary = z.infer<typeof submissionSummarySchema>;

export const QUEUE_KINDS = ['submission', 'essay'] as const;
export const queueEntrySchema = z.object({
  kind: z.enum(QUEUE_KINDS),
  id: z.string(),
  courseId: idSchema,
  courseTitle: z.string(),
  itemId: idSchema,
  itemTitle: z.string(),
  userId: idSchema,
  userName: z.string(),
  submittedAt: instantSchema,
  dueAt: nullableInstant,
  late: z.boolean(),
});
export type QueueEntry = z.infer<typeof queueEntrySchema>;

export const publishResultSchema = z.object({ published: z.number() });
