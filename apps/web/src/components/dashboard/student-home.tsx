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
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Progress } from '@/components/ui/progress';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { ROUTES } from '@/features/auth/routes';
import { useMyTasks } from '@/features/dashboard/use-dashboard';
import type { MyTasks } from '@/lib/api/schemas/me';
import { DEADLINE_GROUPS, type DeadlineGroup } from '@/lib/utils/deadlines';
import { cn } from '@/lib/utils/cn';
import { formatRelative, formatScore } from '@/lib/utils/format';
import { TaskRow } from './task-row';

const GROUP_TONE: Record<DeadlineGroup, string> = {
  overdue: 'text-danger',
  today: 'text-warning',
  thisWeek: 'text-primary',
  later: 'text-text-muted',
};

function DeadlineGroups({ tasks }: { tasks: MyTasks }) {
  const t = useTranslations('home');
  const total = DEADLINE_GROUPS.reduce((sum, group) => sum + tasks[group].length, 0);
  if (total === 0)
    return (
      <EmptyState icon={PartyPopper} title={t('noTasksTitle')} description={t('noTasksText')} />
    );
  return (
    <div className="flex flex-col gap-4">
      {DEADLINE_GROUPS.filter((group) => tasks[group].length > 0).map((group) => (
        <section key={group} aria-labelledby={`group-${group}`}>
          <h3
            id={`group-${group}`}
            className={cn('mb-1 px-3 text-sm font-semibold', GROUP_TONE[group])}
          >
            {t(`groups.${group}`)} ({tasks[group].length})
          </h3>
          <ul className="flex flex-col">
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
    <div className="flex flex-col gap-6">
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
            avgProgress === null ? undefined : (
              <Progress value={avgProgress} tone="success" label={t('avgProgress')} />
            )
          }
        />
      </StatGrid>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Card>
          <CardHeader>
            <CardTitle>{t('deadlines')}</CardTitle>
          </CardHeader>
          <CardContent className="px-2">
            <DeadlineGroups tasks={data} />
          </CardContent>
        </Card>
        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader>
              <CardTitle>{t('continueLearning')}</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              {data.continueLearning.length === 0 ? (
                <p className="text-sm text-text-muted">{t('continueEmpty')}</p>
              ) : null}
              {data.continueLearning.map((entry) => (
                <Link
                  key={entry.courseId}
                  href={ROUTES.item(entry.courseId, entry.itemId)}
                  className="lift flex flex-col gap-2 rounded-md border border-card-border bg-gradient-to-br from-surface to-primary-soft/50 p-4 shadow-sm hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
                >
                  <span className="flex items-center gap-2 text-sm font-medium">
                    <PlayCircle className="size-4 text-primary" aria-hidden />
                    <span className="truncate">{entry.courseTitle}</span>
                  </span>
                  <span className="truncate text-xs text-text-muted">{entry.itemTitle}</span>
                  <Progress
                    value={entry.progressPercent}
                    label={t('progressLabel', { percent: Math.round(entry.progressPercent) })}
                  />
                </Link>
              ))}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>{t('recentlyGraded')}</CardTitle>
            </CardHeader>
            <CardContent>
              {data.recentlyGraded.length === 0 ? (
                <p className="text-sm text-text-muted">{t('gradedEmpty')}</p>
              ) : null}
              <ul className="flex flex-col gap-2">
                {data.recentlyGraded.map((grade) => (
                  <li key={`${grade.itemId}-${grade.gradedAt}`}>
                    <Link
                      href={ROUTES.item(grade.courseId, grade.itemId)}
                      className="flex items-center gap-3 rounded p-2 hover:bg-surface-muted"
                    >
                      <CheckCircle2 className="size-4 shrink-0 text-success" aria-hidden />
                      <span className="flex min-w-0 flex-1 flex-col">
                        <span className="truncate text-sm font-medium">{grade.itemTitle}</span>
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
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
