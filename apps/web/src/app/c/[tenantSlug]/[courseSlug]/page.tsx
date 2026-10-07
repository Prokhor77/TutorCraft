import { ArrowLeft, BookOpen, Layers, ListChecks, UserRound } from 'lucide-react';
import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getLocale, getTranslations } from 'next-intl/server';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { SiteFooter } from '@/components/landing/site-footer';
import { SiteHeader } from '@/components/landing/site-header';
import { MAIN_CONTENT_ID } from '@/components/layout/skip-link';
import { JsonLd } from '@/components/seo/json-ld';
import { CourseCta } from '@/components/public/course-cta';
import { CoverPlaceholder } from '@/components/public/storefront-course-card';
import { Avatar } from '@/components/ui/avatar';
import { ROUTES } from '@/features/auth/routes';
import { docToPlainText } from '@/lib/blockdoc/doc';
import { mathTextToPlain } from '@/lib/math/inline-math';
import type { PublicCourse } from '@/lib/api/schemas/courses';
import { fetchPublicCourse } from '@/lib/server/public-api';
import { excerpt, pageMetadata } from '@/lib/seo/metadata';
import { absoluteUrl, siteUrl } from '@/lib/seo/site';
import {
  breadcrumbNode,
  courseNode,
  graph,
  ids,
  schoolNode,
  webPageNode,
} from '@/lib/seo/structured-data';

type Params = { params: Promise<{ tenantSlug: string; courseSlug: string }> };
const CHIP =
  'flex items-center gap-2 rounded-full border border-card-border bg-surface px-4 py-2 text-label-lg shadow-sm';

function courseOgImagePath(tenantSlug: string, courseSlug: string): string {
  return `${ROUTES.courseLanding(tenantSlug, courseSlug)}/opengraph-image`;
}

async function courseDescription(course: PublicCourse): Promise<string> {
  const text = excerpt(mathTextToPlain(docToPlainText(course.description)));
  if (text) return text;
  const t = await getTranslations('seo');
  return t('courseDescriptionFallback', { course: course.title, school: course.tenantName });
}

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { tenantSlug, courseSlug } = await params;
  const locale = await getLocale();
  const course = await fetchPublicCourse(tenantSlug, courseSlug, locale).catch(() => null);
  if (!course) return {};
  const t = await getTranslations('seo');
  return pageMetadata({
    path: ROUTES.courseLanding(tenantSlug, courseSlug),
    title: t('courseTitle', { course: course.title, school: course.tenantName }),
    description: await courseDescription(course),
    locale,
    siteName: course.tenantName,
    imagePath: courseOgImagePath(tenantSlug, courseSlug),
    imageAlt: course.title,
  });
}

/** Course (free for students, ADR-012) + its school + breadcrumbs «Главная → школа → курс». */
async function CourseStructuredData({ course }: { course: PublicCourse }) {
  const t = await getTranslations('seo');
  const locale = await getLocale();
  const origin = siteUrl();
  const catalogUrl = absoluteUrl(ROUTES.catalog(course.tenantSlug));
  const url = absoluteUrl(ROUTES.courseLanding(course.tenantSlug, course.slug));
  const description = await courseDescription(course);
  return (
    <JsonLd
      data={graph(
        schoolNode(origin, course.tenantSlug, course.tenantName),
        courseNode(origin, {
          url,
          name: course.title,
          description,
          locale,
          tenantSlug: course.tenantSlug,
          teacherName: course.teacher.name,
          imageUrl: absoluteUrl(courseOgImagePath(course.tenantSlug, course.slug)),
          moduleTitles: course.modules.map((module) => module.title),
        }),
        webPageNode(origin, {
          url,
          type: 'ItemPage',
          name: t('courseTitle', { course: course.title, school: course.tenantName }),
          description,
          locale,
          about: ids.course(url),
          hasBreadcrumb: true,
        }),
        breadcrumbNode(url, [
          { name: t('home'), url: `${origin}/` },
          { name: course.tenantName, url: catalogUrl },
          { name: course.title, url },
        ]),
      )}
    />
  );
}

/** SSR course landing with the enrol CTA (FR-COURSE-HYB-01); courses are free for students (ADR-012). */
export default async function CourseLandingPage({ params }: Params) {
  const { tenantSlug, courseSlug } = await params;
  const locale = await getLocale();
  const t = await getTranslations('storefront');
  const course = await fetchPublicCourse(tenantSlug, courseSlug, locale);
  if (!course) notFound();
  const totalItems = course.modules.reduce((sum, module) => sum + module.itemCount, 0);
  const hasDescription = docToPlainText(course.description).trim().length > 0;

  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <CourseStructuredData course={course} />
      <SiteHeader brandName={course.tenantName} brandHref={ROUTES.catalog(tenantSlug)} />
      <main id={MAIN_CONTENT_ID} className="flex-1">
        <section className="mx-auto max-w-content px-page-x pt-6 md:pt-10">
          <div className="relative overflow-hidden rounded-xl border border-card-border bg-surface p-6 shadow-md md:p-10">
            <div
              aria-hidden
              className="pointer-events-none absolute -left-24 -top-32 size-96 rounded-full bg-primary-soft opacity-80 blur-3xl"
            />
            <div
              aria-hidden
              className="pointer-events-none absolute -bottom-40 left-1/3 size-80 rounded-full bg-success-soft opacity-70 blur-3xl"
            />
            <div className="relative grid grid-cols-1 items-center gap-8 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:gap-12">
              <div className="flex min-w-0 flex-col gap-5">
                <Link
                  href={ROUTES.catalog(tenantSlug)}
                  className="flex w-fit items-center gap-1.5 rounded-full bg-accent/10 px-4 py-1.5 text-label-md text-primary transition-colors duration-fast hover:bg-accent/15 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/25"
                >
                  <ArrowLeft className="size-4" aria-hidden />
                  {course.tenantName}
                </Link>
                <h1 className="text-hero-mobile font-bold md:text-4xl">{course.title}</h1>
                <div className="flex items-center gap-3">
                  <Avatar name={course.teacher.name} src={course.teacher.avatarUrl} size="md" />
                  <span className="flex flex-col">
                    <span className="text-label-sm uppercase text-text-muted">
                      {t('teacherLabel')}
                    </span>
                    <span className="text-sm font-semibold">{course.teacher.name}</span>
                  </span>
                </div>
                <ul className="flex flex-wrap gap-2">
                  <li className={CHIP}>
                    <Layers className="size-4 text-primary" aria-hidden />
                    {t('modulesCount', { count: course.modules.length })}
                  </li>
                  <li className={CHIP}>
                    <ListChecks className="size-4 text-primary" aria-hidden />
                    {t('itemsCount', { count: totalItems })}
                  </li>
                </ul>
              </div>
              {course.coverUrl ? (
                // eslint-disable-next-line @next/next/no-img-element -- storage URL
                <img
                  src={course.coverUrl}
                  alt=""
                  className="aspect-video w-full rounded-lg object-cover shadow-md"
                />
              ) : (
                <CoverPlaceholder className="hidden aspect-video w-full rounded-lg shadow-sm sm:flex" />
              )}
            </div>
          </div>
        </section>

        <div className="mx-auto grid max-w-content grid-cols-1 gap-gutter px-page-x py-8 md:py-12 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <aside className="lg:col-start-2 lg:row-start-1">
            <div className="flex flex-col gap-5 rounded-lg border border-card-border bg-surface p-6 shadow-md lg:sticky lg:top-[calc(var(--size-header-public)+1.5rem)]">
              <h2 className="font-heading text-xl font-bold">{t('joinCourse')}</h2>
              <CourseCta courseId={course.id} selfEnrolEnabled={course.selfEnrolEnabled} />
              <div className="flex flex-col gap-3 border-t border-border pt-5">
                <h3 className="text-label-md uppercase text-text-muted">{t('includesTitle')}</h3>
                <ul className="flex flex-col gap-2.5 text-sm">
                  <li className="flex items-center gap-3">
                    <span className="flex size-8 items-center justify-center rounded-full bg-primary-soft text-primary">
                      <Layers className="size-4" aria-hidden />
                    </span>
                    {t('modulesCount', { count: course.modules.length })}
                  </li>
                  <li className="flex items-center gap-3">
                    <span className="flex size-8 items-center justify-center rounded-full bg-primary-soft text-primary">
                      <ListChecks className="size-4" aria-hidden />
                    </span>
                    {t('itemsCount', { count: totalItems })}
                  </li>
                  <li className="flex items-center gap-3">
                    <span className="flex size-8 items-center justify-center rounded-full bg-success-soft text-success">
                      <UserRound className="size-4" aria-hidden />
                    </span>
                    {course.teacher.name}
                  </li>
                </ul>
              </div>
            </div>
          </aside>

          <article className="flex min-w-0 flex-col gap-gutter lg:col-start-1 lg:row-start-1">
            {hasDescription ? (
              <section
                aria-labelledby="course-about-title"
                className="flex flex-col gap-4 rounded-lg border border-card-border bg-surface p-6 shadow-sm md:p-8"
              >
                <h2 id="course-about-title" className="flex items-center gap-3 text-xl">
                  <span className="flex size-10 items-center justify-center rounded-full bg-primary-soft text-primary">
                    <BookOpen className="size-5" aria-hidden />
                  </span>
                  {t('aboutCourse')}
                </h2>
                <BlockRenderer doc={course.description} mediaMode="public" />
              </section>
            ) : null}
            <section
              aria-labelledby="course-program-title"
              className="flex flex-col gap-4 rounded-lg border border-card-border bg-surface p-6 shadow-sm md:p-8"
            >
              <h2 id="course-program-title" className="flex items-center gap-3 text-xl">
                <span className="flex size-10 items-center justify-center rounded-full bg-primary-soft text-primary">
                  <ListChecks className="size-5" aria-hidden />
                </span>
                {t('program', { count: totalItems })}
              </h2>
              <ol className="flex flex-col gap-2">
                {course.modules.map((module, index) => (
                  <li
                    key={`${module.title}-${index}`}
                    className="flex items-center gap-4 rounded-md bg-surface-muted px-4 py-3"
                  >
                    <span
                      aria-hidden
                      className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface font-heading text-sm font-bold text-primary shadow-sm"
                    >
                      {index + 1}
                    </span>
                    <span className="min-w-0 flex-1 font-medium">{module.title}</span>
                    <span className="shrink-0 rounded-full bg-surface px-3 py-1 text-label-md text-text-muted">
                      {t('itemsCount', { count: module.itemCount })}
                    </span>
                  </li>
                ))}
              </ol>
            </section>
          </article>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
