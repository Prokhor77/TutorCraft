import { z } from 'zod';
import { http } from '../http';
import type { CursorParams } from '../pagination';
import { nullableInstant, pageSchema, visibilitySchema } from '../schemas/common';

export const platformTenantSchema = z.object({
  id: z.string().uuid(),
  slug: z.string(),
  name: z.string(),
  status: z.enum(['active', 'suspended']),
  createdAt: z.string(),
  usersCount: z.number().int().nonnegative(),
  /** Courses outside the trash. */
  coursesCount: z.number().int().nonnegative(),
  /** Storage quota of the school; null — unlimited. */
  storageQuotaMb: z.number().int().nonnegative().nullable(),
  /** Tutor the school is registered to (`tenant_admin`); null — no owner assigned. */
  owner: z
    .object({
      id: z.string().uuid(),
      email: z.string(),
      firstName: z.string(),
      lastName: z.string(),
    })
    .nullable(),
});
export type PlatformTenant = z.infer<typeof platformTenantSchema>;

export const FILE_PURPOSES = [
  'content',
  'video',
  'submission',
  'cover',
  'avatar',
  'import',
] as const;

const bytes = z.number().int().nonnegative();

export const tenantStorageSchema = z.object({
  tenantId: z.string().uuid(),
  usedBytes: bytes,
  filesCount: bytes,
  /** Estimated size of the HLS renditions of processed videos (duration × bitrate). */
  hlsBytes: bytes,
  byPurpose: z.array(z.object({ purpose: z.enum(FILE_PURPOSES), bytes, files: bytes })),
});
export type TenantStorage = z.infer<typeof tenantStorageSchema>;

export const courseStorageSchema = z.object({
  courseId: z.string().uuid(),
  title: z.string(),
  inTrash: z.boolean(),
  bytes,
  hlsBytes: bytes,
  filesCount: bytes,
});
export type CourseStorage = z.infer<typeof courseStorageSchema>;

/** A course of any school as the platform administrator sees it (`GET /platform/courses`). */
export const platformCourseSchema = z.object({
  id: z.string().uuid(),
  tenantId: z.string().uuid(),
  title: z.string(),
  shortName: z.string().nullable(),
  slug: z.string(),
  coverUrl: z.string().nullable(),
  visibility: visibilitySchema,
  publishAt: nullableInstant,
  startsAt: nullableInstant,
  endsAt: nullableInstant,
  createdAt: z.string(),
  updatedAt: z.string(),
  /** Tutor who created the course; null — the account is gone. */
  author: z
    .object({
      id: z.string().uuid(),
      email: z.string(),
      firstName: z.string(),
      lastName: z.string(),
    })
    .nullable(),
  studentsCount: z.number().int().nonnegative(),
  /** Active teachers and assistants. */
  staffCount: z.number().int().nonnegative(),
});
export type PlatformCourse = z.infer<typeof platformCourseSchema>;

export type PlatformCoursesQuery = CursorParams & { tenantId?: string; q?: string };

/** Endpoints of the platform main administrator (`platform.manage`). */
export const platformApi = {
  tenants: () => http.request('/platform/tenants', { schema: z.array(platformTenantSchema) }),
  /** Irreversible: the school with every account (owner included), course and file. */
  deleteTenant: (tenantId: string, confirmSlug: string) =>
    http.request(`/platform/tenants/${encodeURIComponent(tenantId)}`, {
      method: 'DELETE',
      body: { confirmSlug },
    }),
  /** Courses of every school (or of `tenantId`), newest first. */
  courses: (query: PlatformCoursesQuery) =>
    http.request('/platform/courses', { query, schema: pageSchema(platformCourseSchema) }),
  storage: () => http.request('/platform/storage', { schema: z.array(tenantStorageSchema) }),
  courseStorage: (tenantId: string) =>
    http.request(`/platform/storage/${encodeURIComponent(tenantId)}/courses`, {
      schema: z.array(courseStorageSchema),
    }),
};
