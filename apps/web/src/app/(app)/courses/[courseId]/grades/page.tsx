'use client';
import { useTranslations } from 'next-intl';
import { CourseGrades } from '@/components/grades/course-grades';
import { PageHeader } from '@/components/ui/page-header';
import { useCourseContext } from '@/features/courses/course-context';

/** Learner's grades inside the course context (Stitch mobile nav «Прогресс»). */
export default function CourseMyGradesPage() {
  const t = useTranslations('grades');
  const { course } = useCourseContext();
  return (
    <>
      <PageHeader eyebrow={course.title} title={t('courseTitle')} />
      <CourseGrades courseId={course.id} />
    </>
  );
}
