'use client';
import { ClipboardCheck, MessagesSquare } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { CountBadge } from '@/components/ui/badge';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import { useTeacherHome } from '@/features/dashboard/use-dashboard';
import { COUNTER_NAMES, useUiStore } from '@/stores/ui-store';
import { formatRelative } from '@/lib/utils/format';
import { TaskRow } from './task-row';

/** FR-DASH-02: to-grade counters, upcoming deadlines, recent posts. */
export function TeacherHome() {
  const t = useTranslations('home');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { data, isLoading, isError, refetch } = useTeacherHome();
  const liveTotal = useUiStore((state) => state.counters[COUNTER_NAMES.gradingQueue]);

  if (isLoading) return <SkeletonList label={tCommon('loading')} rows={4} />;
  if (isError || !data)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void refetch()}
      />
    );
  const total = liveTotal ?? data.toGradeTotal;

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            {t('toGrade')} <CountBadge count={total} label={t('toGradeCount', { count: total })} />
          </CardTitle>
          <Button asChild size="sm">
            <Link href={ROUTES.grading}>
              <ClipboardCheck aria-hidden /> {t('openInbox')}
            </Link>
          </Button>
        </CardHeader>
        <CardContent>
          {data.toGrade.length === 0 ? (
            <p className="text-sm text-text-muted">{t('allGraded')}</p>
          ) : null}
          <ul className="flex flex-col gap-1">
            {data.toGrade.map((entry) => (
              <li key={entry.courseId}>
                <Link
                  href={`${ROUTES.gradingReview}?courseId=${entry.courseId}`}
                  className="flex items-center justify-between rounded-md px-3 py-2 hover:bg-surface-muted"
                >
                  <span className="truncate text-sm">{entry.courseTitle}</span>
                  <span className="text-sm font-semibold">{entry.count}</span>
                </Link>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>{t('upcomingDeadlines')}</CardTitle>
        </CardHeader>
        <CardContent className="px-2">
          {data.upcomingDeadlines.length === 0 ? (
            <p className="px-3 text-sm text-text-muted">{t('noUpcoming')}</p>
          ) : null}
          <ul>
            {data.upcomingDeadlines.map((task) => (
              <li key={task.itemId}>
                <TaskRow task={task} />
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
      <Card className="lg:col-span-2">
        <CardHeader>
          <CardTitle>{t('recentPosts')}</CardTitle>
        </CardHeader>
        <CardContent>
          {data.recentPosts.length === 0 ? (
            <p className="text-sm text-text-muted">{t('noPosts')}</p>
          ) : null}
          <ul className="flex flex-col gap-1">
            {data.recentPosts.map((post) => (
              <li key={post.discussionId} className="flex items-center gap-3 rounded-md px-3 py-2">
                <MessagesSquare className="size-4 text-text-muted" aria-hidden />
                <Link
                  href={ROUTES.course(post.courseId)}
                  className="min-w-0 flex-1 truncate text-sm font-medium hover:underline"
                >
                  {post.title}
                </Link>
                <span className="shrink-0 text-xs text-text-muted">
                  {post.authorName} · {formatRelative(post.createdAt, locale)}
                </span>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
