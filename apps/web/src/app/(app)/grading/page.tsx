'use client';
import { AlertTriangle, ClipboardCheck, Clock, FolderOpen, Inbox, Keyboard } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { FilterPills } from '@/components/grading/filter-pills';
import { QueueCard } from '@/components/grading/queue-card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { Breadcrumbs, PageHeader, Panel } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { ROUTES } from '@/features/auth/routes';
import { useGradingQueue } from '@/features/assessment/use-assessment';
import { useMyCourses } from '@/features/courses/use-my-courses';
import { flattenPages } from '@/lib/api/pagination';
import { QUEUE_KINDS, type QueueEntry } from '@/lib/api/schemas/assessment';
import { formatPercent } from '@/lib/utils/format';
import { COUNTER_NAMES, useUiStore } from '@/stores/ui-store';

const TEACHING_ROLES = new Set(['teacher', 'assistant']);
const PERCENT = 100;
const MINUTE_MS = 60_000;
const MINUTES_PER_HOUR = 60;
const HOURS_PER_DAY = 24;

type KindFilter = 'all' | (typeof QUEUE_KINDS)[number];
type Age = { value: number; unit: 'minutes' | 'hours' | 'days' };

/** How long the oldest submission in the loaded queue has been waiting, in the largest whole unit. */
function oldestAge(entries: QueueEntry[]): Age | null {
  const oldest = Math.min(...entries.map((entry) => Date.parse(entry.submittedAt)));
  if (!Number.isFinite(oldest)) return null;
  const minutes = Math.max(0, Math.floor((Date.now() - oldest) / MINUTE_MS));
  if (minutes < MINUTES_PER_HOUR) return { value: minutes, unit: 'minutes' };
  const hours = Math.floor(minutes / MINUTES_PER_HOUR);
  if (hours < HOURS_PER_DAY) return { value: hours, unit: 'hours' };
  return { value: Math.floor(hours / HOURS_PER_DAY), unit: 'days' };
}

/** «Входящие на проверку» (FR-GRADE-06, AC-8): one queue across courses, sorted by due date. */
export default function GradingInboxPage() {
  const t = useTranslations('grading');
  const tCommon = useTranslations('common');
  const tNav = useTranslations('nav');
  const tShell = useTranslations('shell');
  const locale = useLocale();
  const [courseId, setCourseId] = useState('');
  const [type, setType] = useState<KindFilter>('all');
  const courses = useMyCourses();
  const queue = useGradingQueue({
    courseId: courseId || undefined,
    type: type === 'all' ? undefined : type,
  });
  const liveCount = useUiStore((state) => state.counters[COUNTER_NAMES.gradingQueue]);
  const entries = flattenPages(queue.data?.pages);
  const teachingCourses = (courses.data ?? []).filter(
    (course) => course.role && TEACHING_ROLES.has(course.role),
  );
  const params = new URLSearchParams({
    ...(courseId ? { courseId } : {}),
    ...(type !== 'all' ? { type } : {}),
  });
  const reviewHref = (entryId?: string) =>
    `${ROUTES.gradingReview}?${new URLSearchParams({ ...Object.fromEntries(params), ...(entryId ? { entry: entryId } : {}) })}`;

  const more = queue.hasNextPage ? '+' : '';
  const waiting = liveCount ?? entries.length;
  const lateEntries = entries.filter((entry) => entry.late);
  const oldest = [...entries].sort((a, b) => a.submittedAt.localeCompare(b.submittedAt))[0];
  const age = oldestAge(entries);
  const courseCount = new Set(entries.map((entry) => entry.courseId)).size;
  const latePercent = entries.length ? (lateEntries.length / entries.length) * PERCENT : 0;

  return (
    <div className="flex flex-col gap-gutter">
      <PageHeader
        className="mb-0"
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[{ label: tNav('home'), href: ROUTES.home }, { label: t('inboxTitle') }]}
          />
        }
        title={t('inboxTitle')}
        meta={
          waiting > 0 ? (
            <Badge tone="warning" dot>
              {t('waitingChip', { count: waiting })}
            </Badge>
          ) : (
            <Badge tone="success" dot>
              {t('emptyTitle')}
            </Badge>
          )
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
      >
        <FilterPills
          label={t('filterType')}
          value={type}
          onChange={setType}
          options={(['all', ...QUEUE_KINDS] as const).map((kind) => ({
            value: kind,
            label: kind === 'all' ? t('allTypes') : t(`kinds.${kind}`),
            count: kind === type && queue.isSuccess ? entries.length : undefined,
          }))}
        />
        <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:items-center sm:gap-4">
          <p className="hidden items-center gap-1.5 text-xs text-text-muted lg:flex">
            <Keyboard className="size-4 shrink-0" aria-hidden /> {t('shortcutsHint')}
          </p>
          <NativeSelect
            aria-label={t('filterCourse')}
            value={courseId}
            onChange={(event) => setCourseId(event.target.value)}
            className="h-10 rounded-full border-transparent bg-surface-muted sm:w-64"
          >
            <option value="">{t('allCourses')}</option>
            {teachingCourses.map((course) => (
              <option key={course.id} value={course.id}>
                {course.title}
              </option>
            ))}
          </NativeSelect>
        </div>
      </PageHeader>

      {queue.isSuccess && entries.length > 0 ? (
        <StatGrid>
          <StatCard
            label={t('statQueue')}
            icon={ClipboardCheck}
            tone="warning"
            value={`${entries.length}${more}`}
            unit={t('worksUnit', { count: entries.length })}
            footer={t('coursesHint', { count: courseCount })}
          />
          <StatCard
            label={t('statLate')}
            icon={AlertTriangle}
            tone="danger"
            value={lateEntries.length}
            unit={t('ofTotal', { total: `${entries.length}${more}` })}
            footer={t('lateShare', { percent: formatPercent(latePercent, locale) })}
          />
          <StatCard
            label={t('statOldest')}
            icon={Clock}
            value={age ? age.value : '—'}
            unit={age ? t(`ageUnits.${age.unit}`, { count: age.value }) : undefined}
            footer={
              oldest ? (
                <span className="line-clamp-2">{`${oldest.userName} · ${oldest.itemTitle}`}</span>
              ) : undefined
            }
          />
          <StatCard
            label={t('statCourses')}
            icon={FolderOpen}
            tone="success"
            value={courseCount}
            footer={t('statCoursesHint')}
          />
        </StatGrid>
      ) : null}

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
      {entries.length > 0 ? (
        <Panel title={t('queue')} description={t('queueHint')}>
          <ul className="grid grid-cols-1 gap-3 lg:grid-cols-2 2xl:grid-cols-3">
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
        </Panel>
      ) : null}
    </div>
  );
}
