import { z } from 'zod';
import { idSchema, instantSchema } from './common';

export const ACTIVITY_KINDS = ['request', 'page_view', 'client_error'] as const;
export const activityKindSchema = z.enum(ACTIVITY_KINDS);
export type ActivityKind = z.infer<typeof activityKindSchema>;

export const ACTIVITY_OUTCOMES = ['all', 'failed', 'errors'] as const;
export type ActivityOutcome = (typeof ACTIVITY_OUTCOMES)[number];

/** One activity-log record (GET /activity-log). `errorStack` is filled only inside a trail. */
export const activityEntrySchema = z.object({
  id: idSchema,
  at: instantSchema,
  kind: activityKindSchema,
  tenantId: z.string().nullable(),
  /** null — anonymous record or a deleted school. */
  tenantName: z.string().nullable(),
  userId: z.string().nullable(),
  actorName: z.string().nullable(),
  actorEmail: z.string().nullable(),
  ip: z.string().nullable(),
  userAgent: z.string().nullable(),
  requestId: z.string().nullable(),
  sessionId: z.string().nullable(),
  page: z.string().nullable(),
  method: z.string().nullable(),
  route: z.string().nullable(),
  path: z.string().nullable(),
  pathParams: z.record(z.string()).nullable(),
  handler: z.string().nullable(),
  status: z.number().int().nullable(),
  durationMs: z.number().nullable(),
  errorCode: z.string().nullable(),
  errorType: z.string().nullable(),
  errorMessage: z.string().nullable(),
  errorStack: z.string().nullable(),
});
export type ActivityEntry = z.infer<typeof activityEntrySchema>;

export const activityTrailSchema = z.object({
  focus: activityEntrySchema,
  anchor: z.enum(['user', 'session', 'ip', 'none']),
  from: instantSchema,
  to: instantSchema,
  events: z.array(activityEntrySchema),
  truncated: z.boolean(),
});
export type ActivityTrail = z.infer<typeof activityTrailSchema>;

export const activitySummarySchema = z.object({
  from: instantSchema,
  to: instantSchema,
  requests: z.number(),
  failedRequests: z.number(),
  serverErrors: z.number(),
  clientErrors: z.number(),
  activeUsers: z.number(),
  p95DurationMs: z.number().nullable(),
  topErrorRoutes: z.array(
    z.object({ method: z.string().nullable(), route: z.string().nullable(), count: z.number() }),
  ),
});
export type ActivitySummary = z.infer<typeof activitySummarySchema>;
