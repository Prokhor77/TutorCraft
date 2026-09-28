import { z } from 'zod';

export const idSchema = z.string().min(1);
export const instantSchema = z.string();
export const nullableInstant = instantSchema.nullable();

export const visibilitySchema = z.enum(['published', 'hidden', 'scheduled']);
export type Visibility = z.infer<typeof visibilitySchema>;

export const COURSE_ROLES = ['teacher', 'assistant', 'student', 'observer', 'guest'] as const;
export const courseRoleSchema = z.enum(COURSE_ROLES);
export type CourseRole = z.infer<typeof courseRoleSchema>;

export const TENANT_ROLES = ['platform_admin', 'tenant_admin', 'category_manager'] as const;
export const tenantRoleSchema = z.enum(TENANT_ROLES);
export type TenantRole = z.infer<typeof tenantRoleSchema>;

export const moneySchema = z.object({ amountMinor: z.number().int(), currency: z.string() });
export type Money = z.infer<typeof moneySchema>;

export const ITEM_TYPES = [
  'page',
  'file',
  'url',
  'folder',
  'video',
  'assignment',
  'quiz',
  'forum',
] as const;
export const itemTypeSchema = z.enum(ITEM_TYPES);
export type ItemType = z.infer<typeof itemTypeSchema>;

export const SUBMISSION_STATUSES = [
  'draft',
  'submitted',
  'submitted_late',
  'graded',
  'returned',
] as const;
export const submissionStatusSchema = z.enum(SUBMISSION_STATUSES);
export type SubmissionStatus = z.infer<typeof submissionStatusSchema>;

export const progressStatusSchema = z.union([
  submissionStatusSchema,
  z.enum(['not_started', 'in_progress']),
]);
export type ProgressStatus = z.infer<typeof progressStatusSchema>;

/** Page<T> — cursor pagination (API-04). */
export function pageSchema<T extends z.ZodTypeAny>(item: T) {
  return z.object({ items: z.array(item), nextCursor: z.string().nullable() });
}
export type Page<T> = { items: T[]; nextCursor: string | null };
