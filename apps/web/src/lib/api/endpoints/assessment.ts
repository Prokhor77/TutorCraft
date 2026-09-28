import { http } from '../http';
import type { BlockDoc } from '../schemas/blockdoc';
import { pageSchema } from '../schemas/common';
import {
  publishResultSchema,
  queueEntrySchema,
  submissionSchema,
  submissionSummarySchema,
} from '../schemas/assessment';

export type GradeSubmissionInput = {
  score: number | null;
  feedback?: BlockDoc;
  feedbackFileIds?: string[];
  returnForRevision?: boolean;
};
export type QueueQuery = { courseId?: string; type?: string; cursor?: string | null };

export const assessmentApi = {
  mySubmission: (itemId: string) =>
    http.request(`/items/${itemId}/my-submission`, { schema: submissionSchema }),
  saveDraft: (itemId: string, body: { text?: BlockDoc; fileIds?: string[] }) =>
    http.request(`/items/${itemId}/my-submission/draft`, {
      method: 'PUT',
      body,
      schema: submissionSchema,
    }),
  submit: (itemId: string, idempotencyKey: string) =>
    http.request(`/items/${itemId}/my-submission/submit`, {
      method: 'POST',
      idempotencyKey,
      schema: submissionSchema,
    }),
  submissions: (
    itemId: string,
    query: { status?: string; groupId?: string; cursor?: string | null },
  ) =>
    http.request(`/items/${itemId}/submissions`, {
      query,
      schema: pageSchema(submissionSummarySchema),
    }),
  submission: (id: string) => http.request(`/submissions/${id}`, { schema: submissionSchema }),
  grade: (id: string, body: GradeSubmissionInput) =>
    http.request(`/submissions/${id}/grade`, { method: 'POST', body, schema: submissionSchema }),
  publishGrades: (itemId: string) =>
    http.request(`/items/${itemId}/grades/publish`, {
      method: 'POST',
      schema: publishResultSchema,
    }),
  extension: (
    itemId: string,
    body: { userId?: string; groupId?: string; dueAt: string; closeAt?: string },
  ) => http.request(`/items/${itemId}/extensions`, { method: 'POST', body }),
  queue: (query: QueueQuery) =>
    http.request('/grading-queue', { query, schema: pageSchema(queueEntrySchema) }),
};
