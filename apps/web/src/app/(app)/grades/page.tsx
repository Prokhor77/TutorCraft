'use client';
import { GraduationCap } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { PageHeader } from '@/components/ui/page-header';
import { Progress } from '@/components/ui/progress';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import { useMyGrades } from '@/features/grades/use-grades';
import { formatPercent } from '@/lib/utils/format';

/** «Мои оценки» — summary across courses (FR-GRADE-08). */
export default function MyGradesPage() {
  const t = useTranslations('grades');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const grades = useMyGrades();
  return (
    <>
      <PageHeader title={t('title')} description={t('description')} />
      {grades.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {grades.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void grades.refetch()}
        />
      ) : null}
      {grades.data?.courses.length === 0 ? (
        <EmptyState
          icon={GraduationCap}
          title={t('noGradesTitle')}
          description={t('noGradesText')}
        />
      ) : null}
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {grades.data?.courses.map((course) => (
          <li key={course.courseId}>
            <Link
              href={ROUTES.courseGrades(course.courseId)}
              className="flex flex-col gap-2 rounded-md border border-card-border bg-surface p-4 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
            >
              <span className="font-medium">{course.courseTitle}</span>
              <span className="flex items-baseline gap-2">
                <span className="text-2xl font-bold">
                  {formatPercent(course.finalPercent, locale)}
                </span>
                {course.finalLabel ? (
                  <span className="text-sm text-text-muted">{course.finalLabel}</span>
                ) : null}
              </span>
              {course.finalPercent !== null ? (
                <Progress value={course.finalPercent} label={t('final')} />
              ) : null}
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
