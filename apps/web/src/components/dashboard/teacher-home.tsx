'use client';
import {
  BookOpen,
  CalendarClock,
  ChevronRight,
  ClipboardCheck,
  MessagesSquare,
} from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Avatar } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { CountBadge } from '@/components/ui/badge';
import { ErrorState } from '@/components/ui/error-state';
import { Panel } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { ROUTES } from '@/features/auth/routes';
import { teachingCourseCount, useMyCourses } from '@/features/courses/use-my-courses';
import { useTeacherHome } from '@/features/dashboard/use-dashboard';
import { COUNTER_NAMES, useUiStore } from '@/stores/ui-store';
import { formatRelative } from '@/lib/utils/format';
import { TaskRow } from './task-row';

function EmptyLine({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-md bg-surface-muted/60 px-4 py-3 text-sm text-text-muted">{children}</p>
  );
}

/** FR-DASH-02: to-grade counters, upcoming deadlines, recent posts. */
export function TeacherHome() {
  const t = useTranslations('home');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { data, isLoading, isError, refetch } = useTeacherHome();
  const liveTotal = useUiStore((state) => state.counters[COUNTER_NAMES.gradingQueue]);
  const teachingCount = teachingCourseCount(useMyCourses().data);

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
    <div className="flex flex-col gap-4 md:gap-gutter">
      <StatGrid>
        <StatCard
          label={t('toGrade')}
          icon={ClipboardCheck}
          tone="warning"
          value={total}
          unit={t('worksUnit')}
          footer={t('inCourses', { count: data.toGrade.length })}
        />
        <StatCard
          label={t('upcomingDeadlines')}
          icon={CalendarClock}
          value={data.upcomingDeadlines.length}
          footer={t('deadlinesHint')}
        />
        <StatCard
          label={t('recentPosts')}
          icon={MessagesSquare}
          tone="success"
          value={data.recentPosts.length}
          footer={t('postsHint')}
        />
        <StatCard
          label={t('teachingCourses')}
          icon={BookOpen}
          value={teachingCount}
          footer={t('coursesHint')}
        />
      </StatGrid>
      <div className="grid grid-cols-1 gap-4 md:gap-gutter lg:grid-cols-2">
        <Panel
          title={
            <span className="flex items-center gap-2">
              {t('toGrade')}
              <CountBadge count={total} label={t('toGradeCount', { count: total })} />
            </span>
          }
          actions={
            <Button asChild size="sm">
              <Link href={ROUTES.grading}>
                <ClipboardCheck aria-hidden /> {t('openInbox')}
              </Link>
            </Button>
          }
        >
          {data.toGrade.length === 0 ? (
            <EmptyLine>{t('allGraded')}</EmptyLine>
          ) : (
            <ul className="flex flex-col gap-2">
              {data.toGrade.map((entry) => (
                <li key={entry.courseId}>
                  <Link
                    href={`${ROUTES.gradingReview}?courseId=${entry.courseId}`}
                    className="group flex items-center gap-3 rounded-full border border-transparent bg-surface-muted/50 py-2 pl-2 pr-4 transition-[background-color,border-color,box-shadow] duration-fast hover:border-card-border-hover hover:bg-surface hover:shadow-sm focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
                  >
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary">
                      <BookOpen className="size-4" aria-hidden />
                    </span>
                    <span className="min-w-0 flex-1 truncate text-sm font-semibold">
                      {entry.courseTitle}
                    </span>
                    <span className="shrink-0 rounded-full bg-warning-soft px-2.5 py-0.5 text-label-md text-warning">
                      {t('toGradeCount', { count: entry.count })}
                    </span>
                    <ChevronRight
                      className="size-4 shrink-0 text-outline group-hover:text-primary"
                      aria-hidden
                    />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Panel>
        <Panel title={t('upcomingDeadlines')}>
          {data.upcomingDeadlines.length === 0 ? (
            <EmptyLine>{t('noUpcoming')}</EmptyLine>
          ) : (
            <ul className="flex flex-col gap-2">
              {data.upcomingDeadlines.map((task) => (
                <li key={task.itemId}>
                  <TaskRow task={task} />
                </li>
              ))}
            </ul>
          )}
        </Panel>
        <Panel title={t('recentPosts')} className="lg:col-span-2">
          {data.recentPosts.length === 0 ? (
            <EmptyLine>{t('noPosts')}</EmptyLine>
          ) : (
            <ul className="flex flex-col gap-2">
              {data.recentPosts.map((post) => (
                <li
                  key={post.discussionId}
                  className="relative flex items-center gap-3 rounded-md px-2 py-2 transition-colors duration-fast focus-within:bg-surface-muted hover:bg-surface-muted"
                >
                  <Avatar name={post.authorName} size="md" />
                  <span className="flex min-w-0 flex-1 flex-col">
                    <Link
                      href={ROUTES.course(post.courseId)}
                      className="truncate text-sm font-semibold after:absolute after:inset-0 after:rounded-md focus-visible:outline-none"
                    >
                      {post.title}
                    </Link>
                    <span className="truncate text-xs text-text-muted">
                      {post.authorName} · {formatRelative(post.createdAt, locale)}
                    </span>
                  </span>
                  <MessagesSquare className="size-4 shrink-0 text-outline" aria-hidden />
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  );
}
