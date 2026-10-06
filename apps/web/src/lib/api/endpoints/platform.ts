import { z } from 'zod';
import { http } from '../http';

export const platformTenantSchema = z.object({
  id: z.string().uuid(),
  slug: z.string(),
  name: z.string(),
  status: z.enum(['active', 'suspended']),
  createdAt: z.string(),
  usersCount: z.number().int().nonnegative(),
  /** Storage quota of the school; null — unlimited. */
  storageQuotaMb: z.number().int().nonnegative().nullable(),
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

/** Endpoints of the platform main administrator (`platform.manage`). */
export const platformApi = {
  tenants: () => http.request('/platform/tenants', { schema: z.array(platformTenantSchema) }),
  storage: () => http.request('/platform/storage', { schema: z.array(tenantStorageSchema) }),
  courseStorage: (tenantId: string) =>
    http.request(`/platform/storage/${encodeURIComponent(tenantId)}/courses`, {
      schema: z.array(courseStorageSchema),
    }),
};
