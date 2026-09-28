'use client';
import { ArrowUpRight, GraduationCap } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
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
  const count = grades.data?.courses.length ?? 0;
  return (
    <>
      <PageHeader
        title={t('title')}
        meta={
          grades.isSuccess && count > 0 ? (
            <Badge tone="primary" dot>
              {t('coursesCount', { count })}
            </Badge>
          ) : null
        }
        description={t('description')}
      />
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
      <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:gap-gutter xl:grid-cols-3">
        {grades.data?.courses.map((course) => (
          <li key={course.courseId}>
            <Link
              href={ROUTES.courseGrades(course.courseId)}
              className="group flex h-full flex-col gap-4 rounded-lg border border-card-border bg-surface p-5 shadow-sm transition-[box-shadow,border-color] duration-fast hover:border-card-border-hover hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 sm:p-6"
            >
              <span className="flex items-start gap-3">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary">
                  <GraduationCap className="size-5" aria-hidden />
                </span>
                <span className="min-w-0 flex-1 font-heading text-lg font-semibold leading-snug">
                  {course.courseTitle}
                </span>
                <span
                  className="flex size-8 shrink-0 items-center justify-center rounded-full bg-surface-muted text-primary transition-colors duration-fast group-hover:bg-primary group-hover:text-primary-foreground"
                  aria-hidden
                >
                  <ArrowUpRight className="size-4" />
                </span>
              </span>
              <span className="mt-auto flex flex-col gap-2">
                <span className="text-label-md uppercase text-text-muted">{t('final')}</span>
                <span className="flex flex-wrap items-baseline gap-2">
                  <span className="font-heading text-4xl font-bold tracking-tight">
                    {formatPercent(course.finalPercent, locale)}
                  </span>
                  {course.finalLabel ? <Badge tone="success">{course.finalLabel}</Badge> : null}
                </span>
                {course.finalPercent !== null ? (
                  <Progress value={course.finalPercent} label={t('final')} />
                ) : (
                  <span className="text-xs text-text-muted">{t('noFinalYet')}</span>
                )}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
