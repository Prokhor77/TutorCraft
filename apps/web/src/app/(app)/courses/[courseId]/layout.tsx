'use client';
import { useParams, usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { CourseHeader, PreviewBanner } from '@/components/course/course-header';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { CourseProvider } from '@/features/courses/course-context';
import { useCourse } from '@/features/courses/use-courses';
import { HTTP_STATUS, isApiProblem } from '@/lib/api/problem';

/** Quiz attempts run in focus mode: no course header/tabs to distract (SPEC §10 «Прохождение теста»). */
const FOCUS_MODE_PATTERN = /\/attempts\/[^/]+$/;
/** The course headline belongs to the builder root; sub-pages render their own Stitch page headers. */
const COURSE_ROOT_PATTERN = /^\/courses\/[^/]+$/;

export default function CourseLayout({ children }: { children: ReactNode }) {
  const { courseId } = useParams<{ courseId: string }>();
  const t = useTranslations('course');
  const tCommon = useTranslations('common');
  const course = useCourse(courseId);
  const pathname = usePathname();
  const focusMode = FOCUS_MODE_PATTERN.test(pathname);
  if (course.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (course.isError || !course.data) {
    const notFound = isApiProblem(course.error) && course.error.status === HTTP_STATUS.notFound;
    return (
      <ErrorState
        title={notFound ? t('notFound') : t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={notFound ? undefined : () => void course.refetch()}
      />
    );
  }
  return (
    <CourseProvider course={course.data}>
      {focusMode ? null : <PreviewBanner />}
      {COURSE_ROOT_PATTERN.test(pathname) ? <CourseHeader /> : null}
      {children}
    </CourseProvider>
  );
}
