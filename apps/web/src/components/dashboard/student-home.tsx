'use client';
import {
  AlertTriangle,
  CalendarClock,
  CalendarDays,
  CheckCircle2,
  PartyPopper,
  PlayCircle,
  TrendingUp,
} from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Panel } from '@/components/ui/page-header';
import { Progress } from '@/components/ui/progress';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { ROUTES } from '@/features/auth/routes';
import { useMyTasks } from '@/features/dashboard/use-dashboard';
import type { MyTasks } from '@/lib/api/schemas/me';
import { DEADLINE_GROUPS, type DeadlineGroup } from '@/lib/utils/deadlines';
import { formatRelative, formatScore } from '@/lib/utils/format';
import { TaskRow } from './task-row';

const GROUP_TONE: Record<DeadlineGroup, BadgeTone> = {
  overdue: 'danger',
  today: 'warning',
  thisWeek: 'primary',
  later: 'neutral',
};

function DeadlineGroups({ tasks }: { tasks: MyTasks }) {
  const t = useTranslations('home');
  const total = DEADLINE_GROUPS.reduce((sum, group) => sum + tasks[group].length, 0);
  if (total === 0)
    return (
      <EmptyState icon={PartyPopper} title={t('noTasksTitle')} description={t('noTasksText')} />
    );
  return (
    <div className="flex flex-col gap-5">
      {DEADLINE_GROUPS.filter((group) => tasks[group].length > 0).map((group) => (
        <section key={group} aria-labelledby={`group-${group}`} className="flex flex-col gap-2">
          <h3 id={`group-${group}`} className="flex items-center gap-2 px-1">
            <Badge tone={GROUP_TONE[group]} dot className="uppercase">
              {t(`groups.${group}`)}
            </Badge>
            <span className="font-sans text-label-md text-text-muted">{tasks[group].length}</span>
          </h3>
          <ul className="flex flex-col gap-2">
            {tasks[group].map((task) => (
              <li key={task.itemId}>
                <TaskRow task={task} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** FR-DASH-01 «Мои задачи». */
export function StudentHome() {
  const t = useTranslations('home');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { data, isLoading, isError, refetch } = useMyTasks();

  if (isLoading) return <SkeletonList label={tCommon('loading')} rows={5} />;
  if (isError || !data)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void refetch()}
      />
    );

  const progressValues = data.continueLearning.map((entry) => entry.progressPercent);
  const avgProgress = progressValues.length
    ? Math.round(progressValues.reduce((sum, value) => sum + value, 0) / progressValues.length)
    : null;

  return (
    <div className="flex flex-col gap-4 md:gap-gutter">
      <StatGrid>
        <StatCard
          label={t('groups.overdue')}
          icon={AlertTriangle}
          tone="danger"
          value={data.overdue.length}
          footer={t('overdueHint')}
        />
        <StatCard
          label={t('groups.today')}
          icon={CalendarClock}
          tone="warning"
          value={data.today.length}
          footer={t('todayHint')}
        />
        <StatCard
          label={t('groups.thisWeek')}
          icon={CalendarDays}
          value={data.thisWeek.length}
          footer={t('weekHint')}
        />
        <StatCard
          label={t('avgProgress')}
          icon={TrendingUp}
          tone="success"
          value={avgProgress === null ? '—' : `${avgProgress}%`}
          footer={
            avgProgress === null ? (
              t('continueEmpty')
            ) : (
              <Progress value={avgProgress} tone="success" label={t('avgProgress')} />
            )
          }
        />
      </StatGrid>
      <div className="grid grid-cols-1 gap-4 md:gap-gutter lg:grid-cols-[minmax(0,1fr)_24rem]">
        <Panel title={t('deadlines')}>
          <DeadlineGroups tasks={data} />
        </Panel>
        <div className="flex min-w-0 flex-col gap-4 md:gap-gutter">
          <Panel title={t('continueLearning')}>
            {data.continueLearning.length === 0 ? (
              <p className="rounded-md bg-surface-muted/60 px-4 py-3 text-sm text-text-muted">
                {t('continueEmpty')}
              </p>
            ) : (
              <ul className="flex flex-col gap-3">
                {data.continueLearning.map((entry) => {
                  const percent = Math.round(entry.progressPercent);
                  return (
                    <li key={entry.courseId}>
                      <Link
                        href={ROUTES.item(entry.courseId, entry.itemId)}
                        className="lift flex flex-col gap-3 rounded-md border border-card-border bg-gradient-to-br from-surface to-primary-soft/60 p-4 shadow-sm hover:border-card-border-hover hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
                      >
                        <span className="flex items-center gap-3">
                          <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-sm">
                            <PlayCircle className="size-5" aria-hidden />
                          </span>
                          <span className="flex min-w-0 flex-1 flex-col">
                            <span className="truncate text-sm font-semibold">
                              {entry.courseTitle}
                            </span>
                            <span className="truncate text-xs text-text-muted">
                              {entry.itemTitle}
                            </span>
                          </span>
                          <span className="shrink-0 font-heading text-lg font-semibold text-primary">
                            {percent}%
                          </span>
                        </span>
                        <Progress
                          value={entry.progressPercent}
                          label={t('progressLabel', { percent })}
                        />
                      </Link>
                    </li>
                  );
                })}
              </ul>
            )}
          </Panel>
          <Panel title={t('recentlyGraded')}>
            {data.recentlyGraded.length === 0 ? (
              <p className="rounded-md bg-surface-muted/60 px-4 py-3 text-sm text-text-muted">
                {t('gradedEmpty')}
              </p>
            ) : (
              <ul className="flex flex-col gap-2">
                {data.recentlyGraded.map((grade) => (
                  <li key={`${grade.itemId}-${grade.gradedAt}`}>
                    <Link
                      href={ROUTES.item(grade.courseId, grade.itemId)}
                      className="flex items-center gap-3 rounded-md px-2 py-2 transition-colors duration-fast hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
                    >
                      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-success-soft text-success">
                        <CheckCircle2 className="size-4" aria-hidden />
                      </span>
                      <span className="flex min-w-0 flex-1 flex-col">
                        <span className="truncate text-sm font-semibold">{grade.itemTitle}</span>
                        <span className="truncate text-xs text-text-muted">
                          {grade.courseTitle} · {formatRelative(grade.gradedAt, locale)}
                        </span>
                      </span>
                      <Badge tone="success">
                        {formatScore(grade.score, grade.maxScore, locale)}
                      </Badge>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </div>
      </div>
    </div>
  );
}
