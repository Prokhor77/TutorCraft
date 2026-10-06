import 'server-only';
import { z } from 'zod';
import { API_BASE_PATH } from '@/lib/api/client';
import {
  publicCourseSchema,
  publicSitemapSchoolSchema,
  type PublicCourse,
  type PublicSitemapSchool,
} from '@/lib/api/schemas/courses';
import { coreApiUrl } from './core-api';

/** Storefront cache (SEO pages): short revalidation keeps course pages fresh without hammering core-api. */
export const PUBLIC_REVALIDATE_SEC = 60;
/** sitemap.xml is re-read by crawlers every few hours at most; an hour of staleness costs nothing. */
const SITEMAP_REVALIDATE_SEC = 3600;
const HTTP_NOT_FOUND = 404;

async function fetchPublic<T extends z.ZodTypeAny>(
  path: string,
  schema: T,
  locale: string,
  revalidate: number = PUBLIC_REVALIDATE_SEC,
): Promise<z.output<T> | null> {
  const response = await fetch(`${coreApiUrl()}${API_BASE_PATH}${path}`, {
    headers: { Accept: 'application/json', 'Accept-Language': locale },
    next: { revalidate },
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

export async function fetchSitemap(): Promise<PublicSitemapSchool[]> {
  const schools = await fetchPublic(
    '/public/sitemap',
    z.array(publicSitemapSchoolSchema),
    'ru',
    SITEMAP_REVALIDATE_SEC,
  );
  return schools ?? [];
}
