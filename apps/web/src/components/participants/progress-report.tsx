'use client';
import { useQuery } from '@tanstack/react-query';
import { Check, Minus } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { gradebookApi } from '@/lib/api/endpoints/gradebook';
import { formatPercent } from '@/lib/utils/format';

/** FR-REPORT-01: student × item completion matrix. */
export function ProgressReport({ courseId }: { courseId: string }) {
  const t = useTranslations('participants');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const report = useQuery({
    queryKey: ['courses', courseId, 'progress-report'],
    queryFn: () => gradebookApi.progressReport(courseId),
  });
  if (report.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (report.isError || !report.data)
    return (
      <ErrorState
        title={t('progressError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void report.refetch()}
      />
    );
  const { items, rows } = report.data;
  return (
    <div className="max-h-[70dvh] overflow-auto rounded-lg border border-border bg-surface">
      <table className="w-full border-separate border-spacing-0 text-sm">
        <thead>
          <tr>
            <th
              scope="col"
              className="sticky left-0 top-0 z-20 min-w-44 border-b border-r border-border bg-surface-muted px-3 py-2 text-left text-xs font-semibold uppercase text-text-muted"
            >
              {t('name')}
            </th>
            {items.map((item) => (
              <th
                key={item.id}
                scope="col"
                className="sticky top-0 z-10 min-w-28 border-b border-border bg-surface-muted px-2 py-2 text-center text-xs font-medium text-text-muted"
              >
                <span className="line-clamp-2">{item.title}</span>
              </th>
            ))}
            <th
              scope="col"
              className="sticky top-0 z-10 border-b border-l border-border bg-surface-muted px-3 py-2 text-right text-xs font-semibold uppercase text-text-muted"
            >
              %
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.userId}>
              <th
                scope="row"
                className="sticky left-0 border-b border-r border-border bg-surface px-3 py-1.5 text-left font-medium"
              >
                {row.userName}
              </th>
              {items.map((item) => {
                const done = row.completed.includes(item.id);
                return (
                  <td key={item.id} className="border-b border-border px-2 py-1.5 text-center">
                    {done ? (
                      <Check className="mx-auto size-4 text-success" aria-label={t('done')} />
                    ) : (
                      <Minus className="mx-auto size-4 text-text-muted" aria-label={t('notDone')} />
                    )}
                  </td>
                );
              })}
              <td className="border-b border-l border-border px-3 py-1.5 text-right tabular-nums">
                {formatPercent(row.percent, locale)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
