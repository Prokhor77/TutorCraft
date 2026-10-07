import type { MetadataRoute } from 'next';
import { ROUTES } from '@/features/auth/routes';
import { fetchSitemap } from '@/lib/server/public-api';
import { absoluteUrl, indexingAllowed } from '@/lib/seo/site';

export const dynamic = 'force-dynamic';

/**
 * Only pages with `robots('public')`: the landing, school storefronts and course landings. Legal, auth and app
 * pages are `noindex` and must not be listed — a sitemap URL with `noindex` is a contradiction search consoles
 * flag. No `changefreq` / `priority`: Google ignores both; `lastModified` comes from real course `updated_at`.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  if (!indexingAllowed()) return [];
  const landing: MetadataRoute.Sitemap[number] = { url: absoluteUrl(ROUTES.landing) };
  const schools = await fetchSitemap().catch((error: unknown) => {
    // A core-api outage must not turn sitemap.xml into a 500 — crawlers would back off the whole site.
    console.error('sitemap: storefront index unavailable', error);
    return [];
  });
  return [
    landing,
    ...schools.flatMap((school) => [
      {
        url: absoluteUrl(ROUTES.catalog(school.tenantSlug)),
        ...(school.lastModified ? { lastModified: school.lastModified } : {}),
      },
      ...school.courses.map((course) => ({
        url: absoluteUrl(ROUTES.courseLanding(school.tenantSlug, course.slug)),
        ...(course.lastModified ? { lastModified: course.lastModified } : {}),
      })),
    ]),
  ];
}
