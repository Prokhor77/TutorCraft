'use client';
import { ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { CourseGrades } from '@/components/grades/course-grades';
import { Button } from '@/components/ui/button';
import { Breadcrumbs, PageHeader } from '@/components/ui/page-header';
import { ROUTES } from '@/features/auth/routes';
import { useMyGrades } from '@/features/grades/use-grades';

export default function CourseGradesPage() {
  const { courseId } = useParams<{ courseId: string }>();
  const t = useTranslations('grades');
  const courseTitle = useMyGrades().data?.courses.find(
    (course) => course.courseId === courseId,
  )?.courseTitle;
  return (
    <>
      <PageHeader
        breadcrumbs={
          <Breadcrumbs
            label={t('breadcrumbs')}
            items={[
              { label: t('title'), href: ROUTES.grades },
              { label: courseTitle ?? t('courseTitle') },
            ]}
          />
        }
        title={courseTitle ?? t('courseTitle')}
        description={courseTitle ? t('courseTitle') : undefined}
        actions={
          <Button asChild variant="secondary" size="sm">
            <Link href={ROUTES.grades}>
              <ArrowLeft aria-hidden /> {t('allCourses')}
            </Link>
          </Button>
        }
      />
      <CourseGrades courseId={courseId} />
    </>
  );
}
