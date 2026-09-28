import { z } from 'zod';
import { http } from '../http';

export const platformTenantSchema = z.object({
  id: z.string().uuid(),
  slug: z.string(),
  name: z.string(),
  status: z.enum(['active', 'suspended']),
  createdAt: z.string(),
  usersCount: z.number().int().nonnegative(),
});
export type PlatformTenant = z.infer<typeof platformTenantSchema>;

/** Endpoints of the platform main administrator (`platform.manage`). */
export const platformApi = {
  tenants: () => http.request('/platform/tenants', { schema: z.array(platformTenantSchema) }),
};
