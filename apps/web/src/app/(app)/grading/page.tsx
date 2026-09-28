'use client';
import { ClipboardCheck, Inbox, Keyboard } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { QueueCard } from '@/components/grading/queue-card';
import { CountBadge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { PageHeader } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import { useGradingQueue } from '@/features/assessment/use-assessment';
import { useMyCourses } from '@/features/courses/use-my-courses';
import { flattenPages } from '@/lib/api/pagination';
import { QUEUE_KINDS } from '@/lib/api/schemas/assessment';
import { COUNTER_NAMES, useUiStore } from '@/stores/ui-store';

const TEACHING_ROLES = new Set(['teacher', 'assistant']);

/** «Входящие на проверку» (FR-GRADE-06, AC-8): one queue across courses, sorted by due date. */
export default function GradingInboxPage() {
  const t = useTranslations('grading');
  const tCommon = useTranslations('common');
  const [courseId, setCourseId] = useState('');
  const [type, setType] = useState('');
  const courses = useMyCourses();
  const queue = useGradingQueue({ courseId: courseId || undefined, type: type || undefined });
  const liveCount = useUiStore((state) => state.counters[COUNTER_NAMES.gradingQueue]);
  const entries = flattenPages(queue.data?.pages);
  const teachingCourses = (courses.data ?? []).filter(
    (course) => course.role && TEACHING_ROLES.has(course.role),
  );
  const params = new URLSearchParams({
    ...(courseId ? { courseId } : {}),
    ...(type ? { type } : {}),
  });
  const reviewHref = (entryId?: string) =>
    `${ROUTES.gradingReview}?${new URLSearchParams({ ...Object.fromEntries(params), ...(entryId ? { entry: entryId } : {}) })}`;

  return (
    <>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {t('inboxTitle')}{' '}
            <CountBadge
              count={liveCount ?? entries.length}
              label={t('count', { count: liveCount ?? entries.length })}
            />
          </span>
        }
        description={t('inboxDescription')}
        actions={
          entries.length > 0 ? (
            <Button asChild>
              <Link href={reviewHref(entries[0]?.id)}>
                <ClipboardCheck aria-hidden /> {t('startReview')}
              </Link>
            </Button>
          ) : null
        }
      />
      <div className="mb-4 flex flex-col gap-2 rounded-md border border-card-border bg-surface p-3 shadow-sm sm:flex-row sm:items-center">
        <NativeSelect
          aria-label={t('filterCourse')}
          value={courseId}
          onChange={(event) => setCourseId(event.target.value)}
          className="sm:w-72"
        >
          <option value="">{t('allCourses')}</option>
          {teachingCourses.map((course) => (
            <option key={course.id} value={course.id}>
              {course.title}
            </option>
          ))}
        </NativeSelect>
        <NativeSelect
          aria-label={t('filterType')}
          value={type}
          onChange={(event) => setType(event.target.value)}
          className="sm:w-56"
        >
          <option value="">{t('allTypes')}</option>
          {QUEUE_KINDS.map((kind) => (
            <option key={kind} value={kind}>
              {t(`kinds.${kind}`)}
            </option>
          ))}
        </NativeSelect>
        <p className="flex items-center gap-1.5 text-xs text-text-muted sm:ml-auto">
          <Keyboard className="size-4" aria-hidden /> {t('shortcutsHint')}
        </p>
      </div>
      {queue.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {queue.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void queue.refetch()}
        />
      ) : null}
      {queue.isSuccess && entries.length === 0 ? (
        <EmptyState icon={Inbox} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      <ul className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        {entries.map((entry) => (
          <li key={entry.id}>
            <QueueCard entry={entry} href={reviewHref(entry.id)} showCourse chevron />
          </li>
        ))}
      </ul>
      <LoadMore
        hasMore={!!queue.hasNextPage}
        loading={queue.isFetchingNextPage}
        onClick={() => void queue.fetchNextPage()}
        label={tCommon('loadMore')}
      />
    </>
  );
}
