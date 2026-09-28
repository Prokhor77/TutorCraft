import { BookOpen } from 'lucide-react';
import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getLocale, getTranslations } from 'next-intl/server';
import { SiteFooter } from '@/components/landing/site-footer';
import { SiteHeader } from '@/components/landing/site-header';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ROUTES } from '@/features/auth/routes';
import { fetchCatalog } from '@/lib/server/public-api';
import { formatMoney } from '@/lib/utils/money';

type Params = { params: Promise<{ tenantSlug: string }> };

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { tenantSlug } = await params;
  const [t, locale] = [await getTranslations('storefront'), await getLocale()];
  const courses = await fetchCatalog(tenantSlug, locale).catch(() => null);
  const tenantName = courses?.[0]?.tenantName ?? tenantSlug;
  const title = t('catalogTitle', { school: tenantName });
  return {
    title,
    description: t('catalogDescription', { school: tenantName }),
    openGraph: { title, type: 'website' },
  };
}

/** SSR public catalog (FR-COURSE-HYB-01). */
export default async function CatalogPage({ params }: Params) {
  const { tenantSlug } = await params;
  const locale = await getLocale();
  const t = await getTranslations('storefront');
  const courses = await fetchCatalog(tenantSlug, locale);
  if (!courses) notFound();
  const tenantName = courses[0]?.tenantName ?? tenantSlug;

  return (
    <>
      <SiteHeader brandName={tenantName} brandHref={ROUTES.catalog(tenantSlug)} />
      <main id="main-content" className="mx-auto max-w-content px-page-x py-page-y">
        <h1 className="mb-6 text-3xl">{t('catalogTitle', { school: tenantName })}</h1>
        {courses.length === 0 ? (
          <EmptyState
            icon={BookOpen}
            title={t('catalogEmpty')}
            description={t('catalogEmptyHint')}
          />
        ) : (
          <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {courses.map((course) => (
              <li key={course.id}>
                <Link
                  href={ROUTES.courseLanding(tenantSlug, course.slug)}
                  className="flex h-full flex-col overflow-hidden rounded-md border border-card-border bg-surface shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
                >
                  {course.coverUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element -- storage URL
                    <img
                      src={course.coverUrl}
                      alt=""
                      className="aspect-video w-full object-cover"
                    />
                  ) : (
                    <div
                      className="flex aspect-video w-full items-center justify-center bg-gradient-to-br from-primary-soft via-surface-muted to-success-soft/60 text-primary"
                      aria-hidden
                    >
                      <BookOpen className="size-10 opacity-70" />
                    </div>
                  )}
                  <div className="flex flex-1 flex-col gap-2 p-4">
                    <h2 className="text-lg">{course.title}</h2>
                    <p className="text-sm text-text-muted">{course.teacher.name}</p>
                    <div className="mt-auto">
                      <Badge tone={course.price ? 'primary' : 'success'}>
                        {course.price ? formatMoney(course.price, locale) : t('free')}
                      </Badge>
                    </div>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
      <SiteFooter />
    </>
  );
}
