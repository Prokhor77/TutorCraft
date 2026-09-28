import 'server-only';
import { z } from 'zod';
import { API_BASE_PATH } from '@/lib/api/client';
import { publicCourseSchema, type PublicCourse } from '@/lib/api/schemas/courses';
import { coreApiUrl } from './core-api';

/** Storefront cache (SEO pages): short revalidation keeps prices fresh without hammering core-api. */
export const PUBLIC_REVALIDATE_SEC = 60;
const HTTP_NOT_FOUND = 404;

async function fetchPublic<T extends z.ZodTypeAny>(
  path: string,
  schema: T,
  locale: string,
): Promise<z.output<T> | null> {
  const response = await fetch(`${coreApiUrl()}${API_BASE_PATH}${path}`, {
    headers: { Accept: 'application/json', 'Accept-Language': locale },
    next: { revalidate: PUBLIC_REVALIDATE_SEC },
  });
  if (response.status === HTTP_NOT_FOUND) return null;
  if (!response.ok) throw new Error(`core-api ${path} responded ${response.status}`);
  const parsed = schema.safeParse(await response.json());
  if (!parsed.success) throw new Error(`core-api ${path}: contract mismatch`);
  return parsed.data;
}

export function fetchCatalog(tenantSlug: string, locale: string): Promise<PublicCourse[] | null> {
  return fetchPublic(
    `/public/${encodeURIComponent(tenantSlug)}/courses`,
    z.array(publicCourseSchema),
    locale,
  );
}

export function fetchPublicCourse(
  tenantSlug: string,
  courseSlug: string,
  locale: string,
): Promise<PublicCourse | null> {
  return fetchPublic(
    `/public/${encodeURIComponent(tenantSlug)}/courses/${encodeURIComponent(courseSlug)}`,
    publicCourseSchema,
    locale,
  );
}
