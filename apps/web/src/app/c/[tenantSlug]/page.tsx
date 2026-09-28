import { BookOpen, GraduationCap, Users } from 'lucide-react';
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { getLocale, getTranslations } from 'next-intl/server';
import { SiteFooter } from '@/components/landing/site-footer';
import { SiteHeader } from '@/components/landing/site-header';
import { MAIN_CONTENT_ID } from '@/components/layout/skip-link';
import { StorefrontCourseCard } from '@/components/public/storefront-course-card';
import { EmptyState } from '@/components/ui/empty-state';
import { ROUTES } from '@/features/auth/routes';
import { fetchCatalog } from '@/lib/server/public-api';
import { initials } from '@/lib/utils/format';

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

/** SSR public catalog (FR-COURSE-HYB-01): school hero card + course cards in the landing card style. */
export default async function CatalogPage({ params }: Params) {
  const { tenantSlug } = await params;
  const locale = await getLocale();
  const t = await getTranslations('storefront');
  const courses = await fetchCatalog(tenantSlug, locale);
  if (!courses) notFound();
  const tenantName = courses[0]?.tenantName ?? tenantSlug;
  const teacherCount = new Set(courses.map((course) => course.teacher.name)).size;

  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <SiteHeader brandName={tenantName} brandHref={ROUTES.catalog(tenantSlug)} />
      <main id={MAIN_CONTENT_ID} className="flex-1">
        <section className="mx-auto max-w-content px-page-x pt-6 md:pt-10">
          <div className="relative overflow-hidden rounded-xl border border-card-border bg-surface px-6 py-10 shadow-md md:px-12 md:py-14">
            <div
              aria-hidden
              className="pointer-events-none absolute -right-24 -top-32 size-96 rounded-full bg-primary-soft opacity-80 blur-3xl"
            />
            <div
              aria-hidden
              className="pointer-events-none absolute -bottom-40 right-1/4 size-80 rounded-full bg-success-soft opacity-80 blur-3xl"
            />
            <div className="relative flex flex-col gap-6 md:flex-row md:items-center md:gap-10">
              <span
                aria-hidden
                className="flex size-20 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-primary-container font-heading text-3xl font-bold text-primary-foreground shadow-glow md:size-28 md:text-4xl"
              >
                {initials(tenantName)}
              </span>
              <div className="flex min-w-0 flex-col gap-3">
                <span className="w-fit rounded-full bg-accent/10 px-4 py-1.5 text-label-md uppercase text-primary">
                  {t('catalogEyebrow')}
                </span>
                <h1 className="text-hero-mobile font-bold md:text-4xl">{tenantName}</h1>
                <p className="max-w-2xl text-base text-text-muted">
                  {t('catalogDescription', { school: tenantName })}
                </p>
                {courses.length > 0 ? (
                  <ul className="flex flex-wrap gap-2">
                    <li className="flex items-center gap-2 rounded-full border border-card-border bg-surface px-4 py-2 text-label-lg shadow-sm">
                      <BookOpen className="size-4 text-primary" aria-hidden />
                      {t('coursesCount', { count: courses.length })}
                    </li>
                    <li className="flex items-center gap-2 rounded-full border border-card-border bg-surface px-4 py-2 text-label-lg shadow-sm">
                      <Users className="size-4 text-primary" aria-hidden />
                      {t('teachersCount', { count: teacherCount })}
                    </li>
                  </ul>
                ) : null}
              </div>
            </div>
          </div>
        </section>
        <section
          aria-labelledby="catalog-courses-title"
          className="mx-auto flex max-w-content flex-col gap-6 px-page-x py-10 md:py-14"
        >
          <div className="flex items-center gap-3">
            <span className="flex size-10 items-center justify-center rounded-full bg-primary-soft text-primary">
              <GraduationCap className="size-5" aria-hidden />
            </span>
            <h2 id="catalog-courses-title" className="text-2xl">
              {t('coursesHeading')}
            </h2>
          </div>
          {courses.length === 0 ? (
            <EmptyState
              icon={BookOpen}
              title={t('catalogEmpty')}
              description={t('catalogEmptyHint')}
            />
          ) : (
            <ul className="grid grid-cols-1 gap-gutter sm:grid-cols-2 lg:grid-cols-3">
              {courses.map((course) => (
                <li key={course.id}>
                  <StorefrontCourseCard course={course} tenantSlug={tenantSlug} locale={locale} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}
