'use client';
import {
  CheckCircle2,
  ClipboardList,
  GraduationCap,
  MessageSquareText,
  Percent,
} from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Panel } from '@/components/ui/page-header';
import { Progress } from '@/components/ui/progress';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard } from '@/components/ui/stat-card';
import { useMyCourseGrades } from '@/features/grades/use-grades';
import { cn } from '@/lib/utils/cn';
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
  const graded = data.items.filter((item) => item.score !== null).length;
  return (
    <div className="flex flex-col gap-4 md:gap-gutter">
      <div className="grid grid-cols-2 gap-3 md:gap-gutter">
        <StatCard
          label={t('final')}
          icon={Percent}
          tone="success"
          value={formatPercent(data.finalPercent, locale)}
          unit={data.finalLabel ?? undefined}
          footer={
            data.finalPercent !== null ? (
              <Progress value={data.finalPercent} tone="success" label={t('final')} />
            ) : (
              t('noFinalYet')
            )
          }
        />
        <StatCard
          label={t('gradedItems')}
          icon={ClipboardList}
          value={graded}
          unit={t('ofTotal', { total: data.items.length })}
          footer={
            data.items.length > 0 ? (
              <Progress value={(graded / data.items.length) * 100} label={t('gradedItems')} />
            ) : undefined
          }
        />
      </div>
      {data.items.length === 0 ? (
        <EmptyState
          icon={GraduationCap}
          title={t('noGradesTitle')}
          description={t('noGradesText')}
        />
      ) : (
        <Panel title={t('itemsTitle')}>
          <ul className="flex flex-col gap-2">
            {data.items.map((item) => {
              const isGraded = item.score !== null;
              return (
                <li
                  key={item.gradeItemId}
                  className="flex flex-col gap-2 rounded-md bg-surface-muted/50 px-3 py-3 sm:px-4"
                >
                  <div className="flex items-center gap-3">
                    <span
                      className={cn(
                        'flex size-9 shrink-0 items-center justify-center rounded-full',
                        isGraded ? 'bg-success-soft text-success' : 'bg-surface text-outline',
                      )}
                    >
                      {isGraded ? (
                        <CheckCircle2 className="size-4" aria-hidden />
                      ) : (
                        <ClipboardList className="size-4" aria-hidden />
                      )}
                    </span>
                    <span className="min-w-0 flex-1 text-sm font-semibold">{item.name}</span>
                    <Badge tone={isGraded ? 'success' : 'neutral'} className="tabular-nums">
                      {formatScore(item.score, item.maxScore, locale)}
                    </Badge>
                  </div>
                  {item.feedback ? (
                    <details className="rounded bg-surface px-3 py-2 shadow-sm sm:ml-12">
                      <summary className="flex cursor-pointer items-center gap-1.5 text-label-lg text-primary">
                        <MessageSquareText className="size-4" aria-hidden /> {t('feedback')}
                      </summary>
                      <BlockRenderer doc={item.feedback} className="mt-2 text-sm" />
                    </details>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </Panel>
      )}
    </div>
  );
}
