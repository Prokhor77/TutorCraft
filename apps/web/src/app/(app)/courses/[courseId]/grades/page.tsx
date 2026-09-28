'use client';
import { useTranslations } from 'next-intl';
import { CourseGrades } from '@/components/grades/course-grades';
import { Breadcrumbs, PageHeader } from '@/components/ui/page-header';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';

/** Learner's grades inside the course context (Stitch mobile nav «Прогресс»). */
export default function CourseMyGradesPage() {
  const t = useTranslations('grades');
  const tShell = useTranslations('shell');
  const { course } = useCourseContext();
  return (
    <>
      <PageHeader
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[
              { label: tShell('myCourses'), href: ROUTES.courses },
              { label: course.title, href: ROUTES.course(course.id) },
              { label: tShell('myGrades') },
            ]}
          />
        }
        title={t('courseTitle')}
      />
      <CourseGrades courseId={course.id} />
    </>
  );
}
