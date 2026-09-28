'use client';
import { AlertOctagon, Bug, MousePointerClick, Users } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { Skeleton } from '@/components/ui/skeleton';
import { useActivitySummary } from '@/features/activity/use-activity';

/** Last 24 hours at a glance: volume, server and browser errors, active people, top failing places. */
export function ActivitySummaryCards({
  includeAnonymous,
  onRouteClick,
}: {
  includeAnonymous: boolean;
  onRouteClick: (route: string) => void;
}) {
  const t = useTranslations('adminActivity.summary');
  const locale = useLocale();
  const summary = useActivitySummary(includeAnonymous);
  if (summary.isError) return null;
  if (!summary.data) return <Skeleton className="h-32 w-full" />;
  const data = summary.data;
  const number = (value: number) => value.toLocaleString(locale);
  return (
    <div className="flex flex-col gap-3">
      <StatGrid>
        <StatCard
          label={t('requests')}
          value={number(data.requests)}
          icon={MousePointerClick}
          footer={t('failed', { count: data.failedRequests })}
        />
        <StatCard
          label={t('serverErrors')}
          value={number(data.serverErrors)}
          icon={AlertOctagon}
          tone={data.serverErrors > 0 ? 'danger' : 'success'}
          footer={data.p95DurationMs === null ? undefined : t('p95', { ms: data.p95DurationMs })}
        />
        <StatCard
          label={t('clientErrors')}
          value={number(data.clientErrors)}
          icon={Bug}
          tone={data.clientErrors > 0 ? 'warning' : 'success'}
        />
        <StatCard
          label={t('activeUsers')}
          value={number(data.activeUsers)}
          icon={Users}
          tone="success"
          footer={t('window')}
        />
      </StatGrid>
      {data.topErrorRoutes.length > 0 ? (
        <section
          aria-label={t('topErrors')}
          className="flex flex-col gap-2 rounded-lg border border-card-border bg-surface p-4 shadow-sm"
        >
          <h2 className="text-label-md uppercase text-text-muted">{t('topErrors')}</h2>
          <ul className="flex flex-wrap gap-2">
            {data.topErrorRoutes.map((item) => {
              const route = item.route ?? '—';
              return (
                <li key={`${item.method ?? ''} ${route}`}>
                  <button
                    type="button"
                    onClick={() => onRouteClick(route)}
                    className="flex items-center gap-2 rounded-full bg-danger-soft px-3 py-1 font-mono text-xs text-danger hover:underline focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
                  >
                    {item.method ? `${item.method} ` : ''}
                    {route}
                    <span className="rounded-full bg-surface px-1.5 font-sans font-semibold">
                      {item.count}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
