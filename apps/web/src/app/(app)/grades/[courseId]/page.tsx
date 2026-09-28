'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { CourseGrades } from '@/components/grades/course-grades';
import { PageHeader } from '@/components/ui/page-header';
import { ROUTES } from '@/features/auth/routes';

export default function CourseGradesPage() {
  const { courseId } = useParams<{ courseId: string }>();
  const t = useTranslations('grades');
  return (
    <>
      <Link href={ROUTES.grades} className="mb-2 inline-block text-sm text-primary hover:underline">
        ← {t('allCourses')}
      </Link>
      <PageHeader title={t('courseTitle')} />
      <CourseGrades courseId={courseId} />
    </>
  );
}
