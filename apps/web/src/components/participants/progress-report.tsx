'use client';
import { Check, Minus } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { Avatar } from '@/components/ui/avatar';
import { ErrorState } from '@/components/ui/error-state';
import { Progress } from '@/components/ui/progress';
import { SkeletonList } from '@/components/ui/skeleton';
import { useProgressReport } from '@/features/gradebook/use-gradebook';
import { formatPercent } from '@/lib/utils/format';

const PERCENT = 100;

/** FR-REPORT-01: student × item completion matrix as a Stitch 2rem table panel (sticky header and name column). */
export function ProgressReport({ courseId }: { courseId: string }) {
  const t = useTranslations('participants');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const report = useProgressReport(courseId);
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
    <section
      aria-label={t('progressTitle')}
      className="flex min-w-0 flex-col overflow-hidden rounded-lg border border-card-border bg-surface shadow-sm"
    >
      <div className="flex flex-col gap-0.5 p-5 sm:p-6">
        <h2 className="text-lg">{t('progressTitle')}</h2>
        <p className="text-xs text-text-muted">
          {t('progressHint', { students: rows.length, items: items.length })}
        </p>
      </div>
      <div className="max-h-[70dvh] overflow-auto border-t border-border">
        <table className="w-full border-separate border-spacing-0 text-sm">
          <thead>
            <tr>
              <th
                scope="col"
                className="sticky left-0 top-0 z-20 min-w-44 border-b border-r border-border bg-surface-muted py-3 pl-5 pr-3 text-left text-label-md uppercase text-text-muted sm:min-w-56 sm:pl-6"
              >
                {t('name')}
              </th>
              {items.map((item) => (
                <th
                  key={item.id}
                  scope="col"
                  className="sticky top-0 z-10 min-w-28 border-b border-border bg-surface-muted px-2 py-3 text-center text-label-md text-text-muted"
                >
                  <span className="line-clamp-2">{item.title}</span>
                </th>
              ))}
              <th
                scope="col"
                className="sticky top-0 z-10 min-w-36 border-b border-l border-border bg-surface-muted py-3 pl-3 pr-5 text-right text-label-md uppercase text-text-muted sm:pr-6"
              >
                %
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.userId} className="group/row">
                <th
                  scope="row"
                  className="sticky left-0 border-b border-r border-border bg-surface py-2.5 pl-5 pr-3 text-left font-medium group-hover/row:bg-surface-muted sm:pl-6"
                >
                  <span className="flex items-center gap-2.5">
                    <Avatar name={row.userName} size="sm" />
                    <span className="truncate">{row.userName}</span>
                  </span>
                </th>
                {items.map((item) => {
                  const done = row.completed.includes(item.id);
                  return (
                    <td
                      key={item.id}
                      className="border-b border-border px-2 py-2 text-center group-hover/row:bg-surface-muted/60"
                    >
                      {done ? (
                        <span className="mx-auto flex size-7 items-center justify-center rounded-full bg-success-soft text-success">
                          <Check className="size-4" aria-label={t('done')} />
                        </span>
                      ) : (
                        <span className="mx-auto flex size-7 items-center justify-center rounded-full bg-surface-muted text-text-muted">
                          <Minus className="size-4" aria-label={t('notDone')} />
                        </span>
                      )}
                    </td>
                  );
                })}
                <td className="border-b border-l border-border py-2 pl-3 pr-5 group-hover/row:bg-surface-muted/60 sm:pr-6">
                  <span className="flex items-center justify-end gap-2.5">
                    <Progress
                      value={row.percent}
                      label={t('progressOf', { name: row.userName })}
                      tone={row.percent >= PERCENT ? 'success' : 'primary'}
                      className="h-1.5 w-16"
                    />
                    <span className="w-11 text-right font-semibold tabular-nums">
                      {formatPercent(row.percent, locale)}
                    </span>
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
