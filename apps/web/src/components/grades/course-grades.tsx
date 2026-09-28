'use client';
import { GraduationCap } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { Card } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Progress } from '@/components/ui/progress';
import { SkeletonList } from '@/components/ui/skeleton';
import { useMyCourseGrades } from '@/features/grades/use-grades';
import { formatPercent, formatScore } from '@/lib/utils/format';

/** FR-GRADE-08: per-course grades with feedback and current final (published only). */
export function CourseGrades({ courseId }: { courseId: string }) {
  const t = useTranslations('grades');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const grades = useMyCourseGrades(courseId);
  if (grades.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (grades.isError || !grades.data)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void grades.refetch()}
      />
    );
  const data = grades.data;
  return (
    <div className="flex flex-col gap-4">
      <Card className="flex flex-col gap-2 p-5">
        <span className="text-sm text-text-muted">{t('final')}</span>
        <span className="text-3xl font-bold">{formatPercent(data.finalPercent, locale)}</span>
        {data.finalLabel ? <span className="text-sm font-medium">{data.finalLabel}</span> : null}
        {data.finalPercent !== null ? (
          <Progress value={data.finalPercent} label={t('final')} />
        ) : null}
      </Card>
      {data.items.length === 0 ? (
        <EmptyState
          icon={GraduationCap}
          title={t('noGradesTitle')}
          description={t('noGradesText')}
        />
      ) : null}
      <ul className="flex flex-col gap-2">
        {data.items.map((item) => (
          <li key={item.gradeItemId}>
            <Card className="flex flex-col gap-2 p-4">
              <div className="flex items-center justify-between gap-3">
                <span className="font-medium">{item.name}</span>
                <span className="font-semibold tabular-nums">
                  {formatScore(item.score, item.maxScore, locale)}
                </span>
              </div>
              {item.feedback ? (
                <details>
                  <summary className="cursor-pointer text-sm text-primary">{t('feedback')}</summary>
                  <BlockRenderer doc={item.feedback} className="mt-2 text-sm" />
                </details>
              ) : null}
            </Card>
          </li>
        ))}
      </ul>
    </div>
  );
}
