'use client';
import {
  BarChart3,
  ClipboardCheck,
  Download,
  GraduationCap,
  Inbox,
  Megaphone,
  Plus,
  Search,
  Table2,
  TrendingUp,
} from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { GradeCellView } from '@/components/gradebook/grade-cell';
import { QueueCard } from '@/components/grading/queue-card';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { LoadMore } from '@/components/ui/load-more';
import { Breadcrumbs, PageHeader, Panel } from '@/components/ui/page-header';
import { Progress } from '@/components/ui/progress';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { ROUTES } from '@/features/auth/routes';
import { useGradingQueue } from '@/features/assessment/use-assessment';
import { flattenPages } from '@/lib/api/pagination';
import { ProgressReport } from '@/components/participants/progress-report';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { GradebookSetupSheet } from '@/components/gradebook/setup-sheet';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Field } from '@/components/ui/field';
import { Input, NativeSelect } from '@/components/ui/input';
import { SkeletonList } from '@/components/ui/skeleton';
import { useCourseContext } from '@/features/courses/course-context';
import { useGroups } from '@/features/enrollment/use-enrollment';
import {
  useGradebook,
  useGradebookMutations,
  useGradebookSetup,
  useGradeCellMutation,
  useProgressReport,
} from '@/features/gradebook/use-gradebook';
import { PERMISSIONS } from '@/lib/access/permissions';
import { formatPercent } from '@/lib/utils/format';

function ManualItemDialog({ courseId }: { courseId: string }) {
  const t = useTranslations('gradebook');
  const tCommon = useTranslations('common');
  const { addManualItem } = useGradebookMutations(courseId);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [maxScore, setMaxScore] = useState('100');
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <Plus aria-hidden /> {t('addColumn')}
        </Button>
      </DialogTrigger>
      <DialogContent title={t('addColumn')} closeLabel={tCommon('close')}>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (!name.trim()) return;
            addManualItem.mutate(
              { name: name.trim(), maxScore: Number(maxScore) },
              { onSuccess: () => setOpen(false) },
            );
          }}
        >
          <Field label={t('columnName')} required>
            <Input value={name} onChange={(event) => setName(event.target.value)} autoFocus />
          </Field>
          <Field label={t('maxScore')}>
            <Input
              type="number"
              min={0}
              value={maxScore}
              onChange={(event) => setMaxScore(event.target.value)}
            />
          </Field>
          <DialogFooter>
            <Button type="submit" loading={addManualItem.isPending}>
              {tCommon('create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Gradebook (FR-GRADE-01..07): sticky header/first column, inline editing, formula preview, export, publish. */
function GradebookJournal() {
  const t = useTranslations('gradebook');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { course, can } = useCourseContext();
  const [groupId, setGroupId] = useState('');
  const [query, setQuery] = useState('');
  const book = useGradebook(course.id, groupId || undefined);
  const setup = useGradebookSetup(course.id);
  const groups = useGroups(course.id);
  const cellMutation = useGradeCellMutation(course.id, groupId || undefined);
  const { exportFile, publishItem } = useGradebookMutations(course.id);
  const editable = can(PERMISSIONS.gradeEdit);
  const sourceByGradeItem = new Map(
    (setup.data?.items ?? []).map((item) => [item.gradeItemId, item.sourceItemId]),
  );

  if (book.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (book.isError || !book.data)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void book.refetch()}
      />
    );
  const rows = book.data.rows.filter((row) =>
    row.userName.toLowerCase().includes(query.trim().toLowerCase()),
  );
  const columns = book.data.columns;

  return (
    <section
      aria-label={t('title')}
      className="flex min-w-0 flex-col overflow-hidden rounded-lg border border-card-border bg-surface shadow-sm"
    >
      {setup.data && setup.data.warnings.length > 0 ? (
        <div className="flex flex-col gap-2 px-5 pt-5 sm:px-6">
          {setup.data.warnings.map((warning) => (
            <Alert key={warning.code + warning.message} tone="warning" title={warning.message} />
          ))}
        </div>
      ) : null}
      <div className="flex flex-col gap-3 p-5 sm:p-6 md:flex-row md:flex-wrap md:items-center">
        <div className="flex min-w-0 flex-col gap-0.5 md:mr-auto">
          <h2 className="text-lg">{t('title')}</h2>
          {setup.data ? (
            <p className="text-xs text-text-muted">
              {t('formula')}:{' '}
              <span className="rounded-full bg-surface-muted px-2 py-0.5 font-mono text-text">
                {setup.data.formula}
              </span>
            </p>
          ) : null}
        </div>
        <div className="relative md:w-60">
          <Search
            className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-text-muted"
            aria-hidden
          />
          <Input
            type="search"
            aria-label={t('search')}
            placeholder={t('search')}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            className="h-10 rounded-full border-transparent bg-surface-muted pl-10"
          />
        </div>
        <NativeSelect
          aria-label={t('group')}
          value={groupId}
          onChange={(event) => setGroupId(event.target.value)}
          className="h-10 rounded-full border-transparent bg-surface-muted md:w-48"
        >
          <option value="">{t('allGroups')}</option>
          {groups.data?.map((group) => (
            <option key={group.id} value={group.id}>
              {group.name}
            </option>
          ))}
        </NativeSelect>
        <div className="flex flex-wrap gap-2">
          {can(PERMISSIONS.gradebookConfigure) && setup.data ? (
            <GradebookSetupSheet courseId={course.id} setup={setup.data} />
          ) : null}
          {can(PERMISSIONS.gradebookConfigure) ? <ManualItemDialog courseId={course.id} /> : null}
          {can(PERMISSIONS.gradeExport) ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="secondary" size="sm" loading={exportFile.isPending}>
                  <Download aria-hidden /> {t('export')}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onSelect={() => exportFile.mutate('csv')}>CSV</DropdownMenuItem>
                <DropdownMenuItem onSelect={() => exportFile.mutate('xlsx')}>XLSX</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
      </div>
      {columns.length === 0 ? (
        <div className="px-5 pb-5 sm:px-6 sm:pb-6">
          <EmptyState icon={Table2} title={t('emptyTitle')} description={t('emptyText')} />
        </div>
      ) : (
        <div className="max-h-[70dvh] overflow-auto border-t border-border">
          <table className="w-full border-separate border-spacing-0 text-sm">
            <thead>
              <tr>
                <th
                  scope="col"
                  className="sticky left-0 top-0 z-30 min-w-40 border-b border-r border-border bg-surface-muted py-3 pl-5 pr-3 text-left text-label-md uppercase text-text-muted sm:min-w-56 sm:pl-6"
                >
                  {t('student')}
                </th>
                {columns.map((column) => {
                  const sourceItemId = sourceByGradeItem.get(column.gradeItemId);
                  return (
                    <th
                      key={column.gradeItemId}
                      scope="col"
                      className="sticky top-0 z-20 min-w-32 border-b border-border bg-surface-muted px-3 py-3 text-center text-label-md text-text-muted"
                    >
                      <span className="flex items-center justify-center gap-1">
                        <span className="line-clamp-2">{column.name}</span>
                        {sourceItemId && can(PERMISSIONS.gradePublish) ? (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={t('publishColumn', { name: column.name })}
                            onClick={() => publishItem.mutate(sourceItemId)}
                          >
                            <Megaphone aria-hidden />
                          </Button>
                        ) : null}
                      </span>
                      <span className="block font-normal">/ {column.maxScore}</span>
                    </th>
                  );
                })}
                <th
                  scope="col"
                  className="sticky top-0 z-20 min-w-32 border-b border-l border-border bg-surface-muted py-3 pl-3 pr-5 text-right text-label-md uppercase text-text-muted sm:right-0 sm:pr-6"
                >
                  {t('final')}
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.userId} className="group/row">
                  <th
                    scope="row"
                    className="sticky left-0 z-10 border-b border-r border-border bg-surface py-2.5 pl-5 pr-3 text-left font-medium group-hover/row:bg-surface-muted sm:pl-6"
                  >
                    <span className="flex items-center gap-2.5">
                      <Avatar name={row.userName} size="sm" />
                      <span className="truncate">{row.userName}</span>
                    </span>
                  </th>
                  {columns.map((column) => (
                    <td
                      key={column.gradeItemId}
                      className="border-b border-border px-3 py-2 text-center group-hover/row:bg-surface-muted/60"
                    >
                      <GradeCellView
                        target={{
                          gradeItemId: column.gradeItemId,
                          userId: row.userId,
                          cell: row.cells[column.gradeItemId],
                        }}
                        maxScore={column.maxScore}
                        label={t('cellLabel', { student: row.userName, column: column.name })}
                        editable={editable}
                        onSave={(score, locked) =>
                          cellMutation.mutate({
                            target: {
                              gradeItemId: column.gradeItemId,
                              userId: row.userId,
                              cell: row.cells[column.gradeItemId],
                            },
                            score,
                            locked,
                          })
                        }
                      />
                    </td>
                  ))}
                  <td className="border-b border-l border-border bg-surface py-2 pl-3 pr-5 text-right group-hover/row:bg-surface-muted sm:sticky sm:right-0 sm:pr-6">
                    <span className="flex flex-col items-end gap-1">
                      <span className="font-heading font-bold tabular-nums">
                        {formatPercent(row.finalPercent, locale)}
                      </span>
                      {row.finalPercent !== null ? (
                        <Progress
                          value={row.finalPercent}
                          label={t('final')}
                          className="h-1.5 w-20"
                        />
                      ) : null}
                      {row.finalLabel ? (
                        <span className="text-xs text-text-muted">{row.finalLabel}</span>
                      ) : null}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="border-t border-border bg-surface-muted/60 px-5 py-3 text-xs text-text-muted sm:px-6">
        {t('legend')}
      </p>
    </section>
  );
}

const ANALYTICS_TABS = ['queue', 'journal', 'progress'] as const;
type AnalyticsTab = (typeof ANALYTICS_TABS)[number];
const PERCENT = 100;

function average(values: number[]): number | null {
  return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null;
}

/** Course grading queue (Stitch «Очередь проверки»): cards open the three-pane review screen. */
function CourseQueue({ courseId }: { courseId: string }) {
  const t = useTranslations('grading');
  const tCommon = useTranslations('common');
  const queue = useGradingQueue({ courseId });
  const entries = flattenPages(queue.data?.pages);
  const reviewHref = (entryId: string) =>
    `${ROUTES.gradingReview}?${new URLSearchParams({ courseId, entry: entryId })}`;
  if (queue.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (queue.isError)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void queue.refetch()}
      />
    );
  if (entries.length === 0)
    return <EmptyState icon={Inbox} title={t('emptyTitle')} description={t('emptyText')} />;
  return (
    <Panel title={t('queue')} description={t('queueHint')}>
      <ul className="grid grid-cols-1 gap-3 lg:grid-cols-2 2xl:grid-cols-3">
        {entries.map((entry) => (
          <li key={entry.id}>
            <QueueCard entry={entry} href={reviewHref(entry.id)} chevron />
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
  );
}

/**
 * «Аналитика» (Stitch «Проверка заданий и аналитика успеваемости»): stat cards from the gradebook, grading queue
 * and progress report, then tabs: queue · gradebook journal · progress matrix (FR-GRADE-01/06, FR-REPORT-01).
 */
export default function AnalyticsPage() {
  const t = useTranslations('analytics');
  const tShell = useTranslations('shell');
  const locale = useLocale();
  const router = useRouter();
  const params = useSearchParams();
  const { course, can } = useCourseContext();
  const canGrade = can(PERMISSIONS.submissionGrade);
  const canProgress = can(PERMISSIONS.completionViewAll);
  const gradebook = useGradebook(course.id);
  const queue = useGradingQueue({ courseId: course.id });
  const report = useProgressReport(course.id, canProgress);
  const tabs = ANALYTICS_TABS.filter(
    (tab) => (tab !== 'queue' || canGrade) && (tab !== 'progress' || canProgress),
  );
  const requested = params.get('tab') as AnalyticsTab | null;
  const tab = requested && tabs.includes(requested) ? requested : tabs[0];
  const queueEntries = flattenPages(queue.data?.pages);
  const queueCount = `${queueEntries.length}${queue.hasNextPage ? '+' : ''}`;
  const lateCount = queueEntries.filter((entry) => entry.late).length;
  const rows = gradebook.data?.rows ?? [];
  const avgFinal = average(
    rows.map((row) => row.finalPercent).filter((value): value is number => value !== null),
  );
  const reportRows = report.data?.rows ?? [];
  const avgProgress = average(reportRows.map((row) => row.percent));
  const finished = reportRows.filter((row) => row.percent >= PERCENT).length;

  const changeTab = (value: string) =>
    router.replace(`?${new URLSearchParams({ tab: value })}`, { scroll: false });

  return (
    <Tabs value={tab} onValueChange={changeTab} className="flex flex-col gap-gutter">
      <PageHeader
        className="mb-0"
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[
              { label: tShell('myCourses'), href: ROUTES.courses },
              { label: course.title, href: ROUTES.course(course.id) },
              { label: tShell('analytics') },
            ]}
          />
        }
        title={t('pageTitle')}
        meta={
          canGrade && queueEntries.length > 0 ? (
            <Badge tone="warning" dot>
              {t('waitingChip', { count: queueCount })}
            </Badge>
          ) : null
        }
        actions={
          canGrade && queueEntries.length > 0 ? (
            <Button asChild>
              <Link
                href={`${ROUTES.gradingReview}?${new URLSearchParams({ courseId: course.id })}`}
              >
                <ClipboardCheck aria-hidden /> {t('startReview')}
              </Link>
            </Button>
          ) : null
        }
      >
        <TabsList>
          {tabs.map((entry) => (
            <TabsTrigger key={entry} value={entry} className="group">
              {t(entry)}
              {entry === 'queue' && queueEntries.length > 0 ? (
                <span className="min-w-5 rounded-full bg-primary px-1.5 text-center text-label-sm leading-5 text-primary-foreground group-data-[state=active]:bg-primary-foreground group-data-[state=active]:text-primary">
                  {queueCount}
                </span>
              ) : null}
              {entry === 'journal' && rows.length > 0 ? (
                <span className="min-w-5 rounded-full bg-surface-container px-1.5 text-center text-label-sm leading-5 group-data-[state=active]:bg-primary-foreground group-data-[state=active]:text-primary">
                  {rows.length}
                </span>
              ) : null}
            </TabsTrigger>
          ))}
        </TabsList>
      </PageHeader>
      <StatGrid>
        <StatCard
          label={t('statAverage')}
          icon={BarChart3}
          value={avgFinal === null ? '—' : formatPercent(avgFinal, locale)}
          footer={t('statAverageHint', { count: rows.length })}
        />
        {canGrade ? (
          <StatCard
            label={t('statQueue')}
            icon={ClipboardCheck}
            tone="warning"
            value={queueCount}
            unit={t('worksUnit')}
            footer={t('lateCount', { count: lateCount })}
          />
        ) : null}
        {canProgress ? (
          <StatCard
            label={t('statProgress')}
            icon={TrendingUp}
            tone="success"
            value={avgProgress === null ? '—' : formatPercent(avgProgress, locale)}
            footer={
              avgProgress === null ? undefined : (
                <Progress value={avgProgress} tone="success" label={t('statProgress')} />
              )
            }
          />
        ) : null}
        {canProgress ? (
          <StatCard
            label={t('statFinished')}
            icon={GraduationCap}
            value={finished}
            unit={t('ofStudents', { total: reportRows.length })}
          />
        ) : null}
      </StatGrid>
      <TabsContent value="queue" className="mt-0">
        <CourseQueue courseId={course.id} />
      </TabsContent>
      <TabsContent value="journal" className="mt-0">
        <GradebookJournal />
      </TabsContent>
      <TabsContent value="progress" className="mt-0">
        <ProgressReport courseId={course.id} />
      </TabsContent>
    </Tabs>
  );
}
