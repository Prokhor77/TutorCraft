'use client';
import { ArrowRight } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { CourseCard } from '@/components/course/course-card';
import { Button } from '@/components/ui/button';
import { Panel } from '@/components/ui/page-header';
import { ROUTES } from '@/features/auth/routes';
import type { CourseCard as CourseCardData } from '@/lib/api/schemas/courses';

const MAX_HOME_COURSES = 6;

/** Home «Мои курсы»: the user's real courses as Stitch cover cards, linking to the full list. */
export function HomeCourses({
  courses,
  className,
}: {
  courses: CourseCardData[];
  className?: string;
}) {
  const t = useTranslations('home');
  if (courses.length === 0) return null;
  const shown = courses.slice(0, MAX_HOME_COURSES);
  return (
    <Panel
      className={className}
      title={
        <span className="flex items-center gap-2">
          {t('myCourses')}
          <span className="rounded-full bg-surface-container px-2 py-0.5 font-sans text-label-md text-text-muted">
            {courses.length}
          </span>
        </span>
      }
      actions={
        <Button asChild variant="secondary" size="sm">
          <Link href={ROUTES.courses}>
            {t('allCourses')} <ArrowRight aria-hidden />
          </Link>
        </Button>
      }
    >
      <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {shown.map((course) => (
          <li key={course.id}>
            <CourseCard course={course} />
          </li>
        ))}
      </ul>
    </Panel>
  );
}
