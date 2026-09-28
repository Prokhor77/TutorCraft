import { Layers } from 'lucide-react';
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { getLocale, getTranslations } from 'next-intl/server';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { CourseCta } from '@/components/public/course-cta';
import { PublicHeader } from '@/components/public/public-header';
import { Avatar } from '@/components/ui/avatar';
import { Card } from '@/components/ui/card';
import { ROUTES } from '@/features/auth/routes';
import { docToPlainText } from '@/lib/blockdoc/doc';
import { fetchPublicCourse } from '@/lib/server/public-api';

type Params = { params: Promise<{ tenantSlug: string; courseSlug: string }> };
const META_DESCRIPTION_MAX = 160;

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { tenantSlug, courseSlug } = await params;
  const course = await fetchPublicCourse(tenantSlug, courseSlug, await getLocale()).catch(
    () => null,
  );
  if (!course) return {};
  const description = docToPlainText(course.description).slice(0, META_DESCRIPTION_MAX);
  return {
    title: course.title,
    description,
    openGraph: {
      title: course.title,
      description,
      type: 'website',
      siteName: course.tenantName,
      images: course.coverUrl ? [course.coverUrl] : undefined,
    },
    twitter: {
      card: course.coverUrl ? 'summary_large_image' : 'summary',
      title: course.title,
      description,
    },
  };
}

/** SSR course landing with Buy / Enroll CTA (FR-COURSE-HYB-01, FR-ENROL-09). */
export default async function CourseLandingPage({ params }: Params) {
  const { tenantSlug, courseSlug } = await params;
  const t = await getTranslations('storefront');
  const course = await fetchPublicCourse(tenantSlug, courseSlug, await getLocale());
  if (!course) notFound();
  const totalItems = course.modules.reduce((sum, module) => sum + module.itemCount, 0);

  return (
    <>
      <PublicHeader brandName={course.tenantName} brandHref={ROUTES.catalog(tenantSlug)} />
      <main id="main-content" className="mx-auto max-w-content px-page-x py-page-y">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <article className="flex min-w-0 flex-col gap-6">
            {course.coverUrl ? (
              // eslint-disable-next-line @next/next/no-img-element -- storage URL
              <img
                src={course.coverUrl}
                alt=""
                className="aspect-[21/9] w-full rounded-xl object-cover"
              />
            ) : null}
            <header className="flex flex-col gap-3">
              <h1 className="text-3xl md:text-4xl">{course.title}</h1>
              <div className="flex items-center gap-2 text-sm text-text-muted">
                <Avatar name={course.teacher.name} src={course.teacher.avatarUrl} size="sm" />
                {course.teacher.name}
              </div>
            </header>
            <BlockRenderer doc={course.description} mediaMode="public" />
            <section className="flex flex-col gap-3">
              <h2 className="text-xl">{t('program', { count: totalItems })}</h2>
              <ol className="flex flex-col gap-2">
                {course.modules.map((module, index) => (
                  <li
                    key={`${module.title}-${index}`}
                    className="flex items-center gap-3 rounded-md border border-border bg-surface px-4 py-3"
                  >
                    <Layers className="size-4 text-primary" aria-hidden />
                    <span className="flex-1">{module.title}</span>
                    <span className="text-sm text-text-muted">
                      {t('itemsCount', { count: module.itemCount })}
                    </span>
                  </li>
                ))}
              </ol>
            </section>
          </article>
          <aside>
            <Card className="sticky top-[calc(var(--size-header)+1rem)] flex flex-col gap-4 p-5">
              <h2 className="text-lg">{t('joinCourse')}</h2>
              <CourseCta
                courseId={course.id}
                price={course.price}
                selfEnrolEnabled={course.selfEnrolEnabled}
              />
            </Card>
          </aside>
        </div>
      </main>
    </>
  );
}
