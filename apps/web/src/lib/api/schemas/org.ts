import { z } from 'zod';
import { brandingSchema, localeSchema } from './auth';
import { idSchema, instantSchema, nullableInstant, tenantRoleSchema } from './common';

export const tenantSettingsSchema = z.object({
  id: idSchema,
  slug: z.string(),
  name: z.string(),
  branding: brandingSchema,
  logoFileId: idSchema.nullable(),
  defaultLocale: localeSchema,
  defaultTimezone: z.string(),
  passwordPolicy: z.object({
    minLength: z.number(),
    requireDigit: z.boolean(),
    requireLetter: z.boolean(),
  }),
  embedWhitelist: z.array(z.string()),
  version: z.number(),
});
export type TenantSettings = z.infer<typeof tenantSettingsSchema>;

export const categorySchema = z.object({
  id: idSchema,
  parentId: idSchema.nullable(),
  name: z.string(),
  position: z.number(),
  courseCount: z.number(),
});
export type Category = z.infer<typeof categorySchema>;

export const USER_STATUSES = ['active', 'suspended', 'invited'] as const;
export const userSummarySchema = z.object({
  id: idSchema,
  email: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  status: z.enum(USER_STATUSES),
  tenantRoles: z.array(tenantRoleSchema),
  lastLoginAt: nullableInstant,
  createdAt: instantSchema,
});
export type UserSummary = z.infer<typeof userSummarySchema>;

export const importRowErrorSchema = z.object({
  row: z.number(),
  field: z.string(),
  code: z.string(),
  message: z.string(),
});
export type ImportRowError = z.infer<typeof importRowErrorSchema>;

export const importPreviewSchema = z.object({
  previewId: idSchema,
  valid: z.number(),
  invalid: z.number(),
  rows: z.array(
    z.object({
      row: z.number(),
      email: z.string(),
      firstName: z.string(),
      lastName: z.string(),
      courseShortName: z.string().optional(),
      errors: z.array(importRowErrorSchema),
    }),
  ),
});
export type ImportPreview = z.infer<typeof importPreviewSchema>;

export const importCommitResultSchema = z.object({
  created: z.number(),
  enrolled: z.number(),
  errors: z.array(importRowErrorSchema),
});
export type ImportCommitResult = z.infer<typeof importCommitResultSchema>;

export const auditEntrySchema = z.object({
  id: idSchema,
  at: instantSchema,
  tenantId: idSchema,
  /** null — the school has been deleted (its audit trail is kept). */
  tenantName: z.string().nullable(),
  actorId: idSchema.nullable(),
  actorName: z.string().nullable(),
  action: z.string(),
  objectType: z.string(),
  objectId: z.string(),
  ip: z.string().nullable(),
  diff: z.record(z.unknown()).nullable(),
});
export type AuditEntry = z.infer<typeof auditEntrySchema>;
