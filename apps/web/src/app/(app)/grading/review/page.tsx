'use client';
import { ChevronLeft, ChevronRight, FileText, Inbox, Keyboard, Search } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Suspense, useEffect, useMemo, useState } from 'react';
import { FilterPills } from '@/components/grading/filter-pills';
import { EssayPreview, SubmissionPreview } from '@/components/grading/submission-preview';
import { emptyGradeDraft, GradePanel, type GradeDraft } from '@/components/grading/grade-panel';
import { GRADING_SHORTCUTS, ShortcutsHelp } from '@/components/grading/shortcuts-help';
import { QueueCard } from '@/components/grading/queue-card';
import { StudentPanel } from '@/components/grading/student-panel';
import { DueLabel } from '@/components/course/item-meta';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { Kbd } from '@/components/ui/kbd';
import { Breadcrumbs, PageHeader, Panel } from '@/components/ui/page-header';
import { Segmented } from '@/components/ui/segmented';
import { SkeletonList } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toast';
import { useHotkeys } from '@/features/app/use-hotkeys';
import { useProblemToast } from '@/features/app/use-problem-toast';
import { ROUTES } from '@/features/auth/routes';
import { parseEssayId } from '@/features/assessment/quick-comments';
import {
  useGradeSubmission,
  useGradingQueue,
  useSubmission,
} from '@/features/assessment/use-assessment';
import { useItem } from '@/features/items/use-item';
import { useAttempt } from '@/features/quiz/use-quiz';
import { quizApi } from '@/lib/api/endpoints/quiz';
import { flattenPages } from '@/lib/api/pagination';
import { QUEUE_KINDS, type QueueEntry } from '@/lib/api/schemas/assessment';
import { isDocEmpty } from '@/lib/blockdoc/doc';
import { cn } from '@/lib/utils/cn';
import { formatRelative } from '@/lib/utils/format';

type ReviewEntry = Pick<QueueEntry, 'kind' | 'id' | 'itemId' | 'userName' | 'itemTitle'> &
  Partial<Pick<QueueEntry, 'courseTitle' | 'submittedAt' | 'late' | 'dueAt'>>;
type QueueKind = (typeof QUEUE_KINDS)[number];
type MobileView = 'review' | 'queue';
/** «Далее в очереди» on phones (Stitch grading-mobile). */
const UP_NEXT_COUNT = 3;
const STICKY_COLUMN =
  'lg:sticky lg:top-[calc(var(--size-header)+1rem)] lg:max-h-[calc(100dvh-var(--size-header)-2rem)]';

function useMaxScore(entry: ReviewEntry | undefined): number | null {
  const item = useItem(entry?.kind === 'submission' ? entry.itemId : '');
  const submission = useSubmission(entry?.kind === 'submission' ? entry.id : null);
  const attempt = useAttempt(
    entry?.kind === 'essay' ? (parseEssayId(entry.id)?.attemptId ?? '') : '',
  );
  if (!entry) return null;
  if (entry.kind === 'essay')
    return (
      attempt.data?.questions.find((question) => question.slot === parseEssayId(entry.id)?.slot)
        ?.points ?? null
    );
  if (submission.data?.grade) return submission.data.grade.maxScore;
  return item.data?.settings.kind === 'assignment' ? item.data.settings.maxScore : null;
}

function draftFromSubmission(submission: ReturnType<typeof useSubmission>['data']): GradeDraft {
  if (!submission?.grade) return emptyGradeDraft();
  return {
    ...emptyGradeDraft(),
    score: submission.grade.score?.toString() ?? '',
    feedback: submission.grade.feedback ?? emptyGradeDraft().feedback,
  };
}

function Review() {
  const t = useTranslations('grading');
  const tCommon = useTranslations('common');
  const tNav = useTranslations('nav');
  const tShell = useTranslations('shell');
  const params = useSearchParams();
  const showProblem = useProblemToast();
  const courseId = params.get('courseId') ?? undefined;
  const itemId = params.get('itemId');
  const type = params.get('type') ?? undefined;
  const requestedSubmission = params.get('submissionId');
  const queue = useGradingQueue({ courseId, type });
  const [currentId, setCurrentId] = useState<string | null>(
    params.get('entry') ?? requestedSubmission,
  );
  const [draft, setDraft] = useState<GradeDraft>(emptyGradeDraft());
  const [helpOpen, setHelpOpen] = useState(false);
  const [essaySaving, setEssaySaving] = useState(false);
  const [search, setSearch] = useState('');
  const [kindFilter, setKindFilter] = useState<'all' | QueueKind>('all');
  const [mobileView, setMobileView] = useState<MobileView>('review');
  const locale = useLocale();
  const grade = useGradeSubmission();

  const entries: ReviewEntry[] = useMemo(() => {
    const list: ReviewEntry[] = flattenPages(queue.data?.pages).filter(
      (entry) => !itemId || entry.itemId === itemId,
    );
    if (requestedSubmission && itemId && !list.some((entry) => entry.id === requestedSubmission)) {
      list.unshift({
        kind: 'submission',
        id: requestedSubmission,
        itemId,
        userName: '',
        itemTitle: '',
      });
    }
    return list;
  }, [queue.data, itemId, requestedSubmission]);

  const index = Math.max(
    0,
    entries.findIndex((entry) => entry.id === currentId),
  );
  const current = entries[index];
  const submission = useSubmission(current?.kind === 'submission' ? current.id : null);
  const maxScore = useMaxScore(current);

  useEffect(() => {
    if (!currentId && entries[0]) setCurrentId(entries[0].id);
  }, [entries, currentId]);
  useEffect(() => setDraft(draftFromSubmission(submission.data)), [submission.data, current?.id]);

  const go = (delta: 1 | -1) => {
    const target = entries[index + delta];
    if (target) setCurrentId(target.id);
  };
  const select = (id: string) => {
    setCurrentId(id);
    setMobileView('review');
  };

  const saveAndNext = async (overrides?: Partial<GradeDraft>) => {
    if (!current) return;
    const values = { ...draft, ...overrides };
    const nextId = entries[index + 1]?.id ?? entries[index - 1]?.id ?? null;
    const score = values.score === '' ? null : Number(values.score);
    if (current.kind === 'essay') {
      const parsed = parseEssayId(current.id);
      if (!parsed || score === null) return;
      setEssaySaving(true);
      try {
        await quizApi.gradeEssay(parsed.attemptId, parsed.slot, {
          score,
          comment: values.comment || undefined,
        });
        void queue.refetch();
      } catch (error) {
        showProblem(error);
        return;
      } finally {
        setEssaySaving(false);
      }
    } else {
      await grade.mutateAsync({
        id: current.id,
        input: {
          score,
          feedback: isDocEmpty(values.feedback) ? undefined : values.feedback,
          returnForRevision: values.returnForRevision || undefined,
        },
      });
    }
    toast({ tone: 'success', title: t('saved') });
    setCurrentId(nextId);
  };

  useHotkeys({
    j: () => go(1),
    k: () => go(-1),
    'mod+enter': () => void saveAndNext().catch(() => undefined),
    '?': () => setHelpOpen(true),
  });

  const needle = search.trim().toLocaleLowerCase();
  const listed = entries.filter(
    (entry) =>
      (kindFilter === 'all' || entry.kind === kindFilter) &&
      (!needle || entry.userName.toLocaleLowerCase().includes(needle)),
  );
  const upNext = entries.slice(index + 1, index + 1 + UP_NEXT_COUNT);
  const kindCount = (kind: 'all' | QueueKind) =>
    kind === 'all' ? entries.length : entries.filter((entry) => entry.kind === kind).length;

  if (queue.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (!current) {
    return (
      <EmptyState
        icon={Inbox}
        title={t('allDoneTitle')}
        description={t('allDoneText')}
        action={
          <Button asChild variant="secondary">
            <Link href={ROUTES.grading}>{t('backToInbox')}</Link>
          </Button>
        }
      />
    );
  }

  const studentName = current.userName || submission.data?.userName || '';
  return (
    <div className="flex flex-col gap-gutter">
      <PageHeader
        className="mb-0"
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[
              { label: tNav('home'), href: ROUTES.home },
              { label: t('inboxTitle'), href: ROUTES.grading },
              { label: t('reviewTitle') },
            ]}
          />
        }
        title={t('reviewTitle')}
        meta={
          <Badge tone="warning" dot>
            {t('waitingChip', { count: entries.length })}
          </Badge>
        }
        actions={
          <>
            <div className="flex items-center gap-1 rounded-full bg-surface-muted p-1 shadow-inner">
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t('shortcuts.previous')}
                disabled={index === 0}
                onClick={() => go(-1)}
              >
                <ChevronLeft aria-hidden />
              </Button>
              <span className="px-1 text-label-md tabular-nums" aria-live="polite">
                {t('position', { current: index + 1, total: entries.length })}
              </span>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t('shortcuts.next')}
                disabled={index >= entries.length - 1}
                onClick={() => go(1)}
              >
                <ChevronRight aria-hidden />
              </Button>
            </div>
            <Button
              variant="secondary"
              size="icon"
              aria-label={t('shortcutsTitle')}
              onClick={() => setHelpOpen(true)}
            >
              <Keyboard aria-hidden />
            </Button>
          </>
        }
      >
        <Segmented
          label={t('mobileView')}
          value={mobileView}
          onChange={setMobileView}
          options={[
            { value: 'review', label: t('grade') },
            { value: 'queue', label: t('queueCount', { count: entries.length }) },
          ]}
          className="w-full lg:hidden"
        />
        <p className="hidden items-center gap-1.5 text-xs text-text-muted lg:flex">
          <Keyboard className="size-4 shrink-0" aria-hidden /> {t('shortcutsHint')}
        </p>
      </PageHeader>

      <div className="grid grid-cols-1 items-start gap-gutter lg:grid-cols-[19rem_minmax(0,1fr)] xl:grid-cols-[20rem_minmax(0,1fr)_19rem]">
        <nav
          aria-label={t('queue')}
          className={cn(
            'flex-col gap-3 rounded-lg border border-card-border bg-surface p-4 shadow-sm lg:flex',
            STICKY_COLUMN,
            mobileView === 'queue' ? 'flex' : 'hidden',
          )}
        >
          <div className="relative">
            <Search
              className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-text-muted"
              aria-hidden
            />
            <Input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('searchStudent')}
              aria-label={t('searchStudent')}
              className="h-10 rounded-full border-transparent bg-surface-muted pl-10"
            />
          </div>
          <FilterPills
            variant="chips"
            label={t('filterType')}
            value={kindFilter}
            onChange={setKindFilter}
            options={(['all', ...QUEUE_KINDS] as const).map((kind) => ({
              value: kind,
              label: kind === 'all' ? t('allTypes') : t(`kinds.${kind}`),
              count: kindCount(kind),
            }))}
          />
          <ul className="-mx-1 flex flex-col gap-2 overflow-y-auto px-1 pb-1">
            {listed.map((entry) => (
              <li key={entry.id}>
                <QueueCard
                  entry={{
                    ...entry,
                    userName: entry.userName || submission.data?.userName || '',
                  }}
                  active={entry.id === current.id}
                  onSelect={() => select(entry.id)}
                />
              </li>
            ))}
          </ul>
        </nav>

        <div
          className={cn(
            'min-w-0 flex-col gap-gutter lg:flex',
            mobileView === 'review' ? 'flex' : 'hidden',
          )}
        >
          <section className="flex flex-wrap items-center gap-4 rounded-lg border border-card-border bg-surface p-5 shadow-sm sm:p-6">
            <Avatar name={studentName || '…'} size="lg" />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <h2 className="truncate text-xl">{studentName}</h2>
              <p className="truncate text-sm text-text-muted">
                {current.itemTitle}
                {current.courseTitle ? ` · ${current.courseTitle}` : ''}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {current.submittedAt ? (
                <Badge tone="neutral">
                  {t('submitted', { when: formatRelative(current.submittedAt, locale) })}
                </Badge>
              ) : null}
              {current.late ? (
                <Badge tone="danger" dot>
                  {t('late')}
                </Badge>
              ) : null}
              <DueLabel
                dueAt={current.dueAt ?? null}
                className="rounded-full bg-warning-soft px-2.5 py-0.5 font-semibold text-warning"
              />
            </div>
          </section>
          <Panel
            title={
              <span className="flex items-center gap-2">
                <span className="flex size-8 items-center justify-center rounded-full bg-primary-soft text-primary">
                  <FileText className="size-4" aria-hidden />
                </span>
                {t('work')}
              </span>
            }
          >
            <div aria-label={t('work')} role="region" className="min-w-0">
              {current.kind === 'submission' ? (
                <SubmissionPreview key={current.id} id={current.id} />
              ) : (
                <EssayPreview id={current.id} />
              )}
            </div>
          </Panel>
          <section aria-label={t('grade')}>
            <GradePanel
              key={current.id}
              kind={current.kind}
              maxScore={maxScore}
              draft={draft}
              onChange={setDraft}
              saving={grade.isPending || essaySaving}
              onSave={(overrides) => void saveAndNext(overrides).catch(() => undefined)}
            />
          </section>
          <div className="flex flex-col gap-gutter xl:hidden">
            <StudentPanel
              entry={{ ...current, userName: studentName }}
              submission={submission.data}
            />
          </div>
          {upNext.length > 0 ? (
            <section aria-label={t('upNext')} className="flex flex-col gap-3 lg:hidden">
              <h2 className="text-lg">{t('upNext')}</h2>
              <ul className="flex flex-col gap-2">
                {upNext.map((entry) => (
                  <li key={entry.id}>
                    <QueueCard entry={entry} onSelect={() => select(entry.id)} chevron />
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>

        <div className="hidden flex-col gap-gutter xl:sticky xl:top-[calc(var(--size-header)+1rem)] xl:flex">
          <StudentPanel
            entry={{ ...current, userName: studentName }}
            submission={submission.data}
          />
          <Panel title={<span className="text-base">{t('shortcutsTitle')}</span>}>
            <dl className="flex flex-col gap-2">
              {GRADING_SHORTCUTS.map((shortcut) => (
                <div key={shortcut.action} className="flex items-center justify-between gap-3">
                  <dt className="text-xs text-text-muted">{t(`shortcuts.${shortcut.action}`)}</dt>
                  <dd className="flex gap-1">
                    {shortcut.keys.map((key) => (
                      <Kbd key={key}>{key}</Kbd>
                    ))}
                  </dd>
                </div>
              ))}
            </dl>
          </Panel>
        </div>
      </div>
      <ShortcutsHelp open={helpOpen} onOpenChange={setHelpOpen} />
    </div>
  );
}

/** Grading screen (FR-ASSIGN-05, AC-8): queue | work + grade | student, J/K, Ctrl+Enter, `?`. */
export default function GradingReviewPage() {
  return (
    <Suspense>
      <Review />
    </Suspense>
  );
}
