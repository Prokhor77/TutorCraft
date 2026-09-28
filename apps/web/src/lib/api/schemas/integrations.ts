import { z } from 'zod';
import { idSchema, instantSchema, nullableInstant } from './common';

export const TOKEN_SCOPES = ['read', 'write'] as const;
export const apiTokenSummarySchema = z.object({
  id: idSchema,
  userId: idSchema,
  name: z.string(),
  scopes: z.array(z.enum(TOKEN_SCOPES)),
  expiresAt: nullableInstant,
  lastUsedAt: nullableInstant,
  createdAt: instantSchema,
});
export type ApiTokenSummary = z.infer<typeof apiTokenSummarySchema>;
export const createdTokenSchema = z.object({ id: idSchema, token: z.string() });

export const WEBHOOK_EVENTS = [
  'enrollment.created',
  'submission.submitted',
  'grade.published',
  'course.completed',
  'order.paid',
] as const;
export const webhookSchema = z.object({
  id: idSchema,
  url: z.string(),
  events: z.array(z.string()),
  createdAt: instantSchema,
});
export type Webhook = z.infer<typeof webhookSchema>;
export const createdWebhookSchema = z.object({ id: idSchema, secret: z.string() });

export const webhookDeliverySchema = z.object({
  id: idSchema,
  event: z.string(),
  status: z.enum(['pending', 'succeeded', 'failed']),
  attempts: z.number(),
  responseCode: z.number().nullable(),
  error: z.string().nullable(),
  createdAt: instantSchema,
  lastAttemptAt: nullableInstant,
  nextAttemptAt: instantSchema,
});
export type WebhookDelivery = z.infer<typeof webhookDeliverySchema>;
