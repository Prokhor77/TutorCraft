'use client';
import { ChevronLeft, ChevronRight, Inbox, Keyboard, Search } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Suspense, useEffect, useMemo, useState } from 'react';
import { EssayPreview, SubmissionPreview } from '@/components/grading/submission-preview';
import { emptyGradeDraft, GradePanel, type GradeDraft } from '@/components/grading/grade-panel';
import { ShortcutsHelp } from '@/components/grading/shortcuts-help';
import { QueueCard } from '@/components/grading/queue-card';
import { DueLabel } from '@/components/course/item-meta';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
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
  Partial<Pick<QueueEntry, 'submittedAt' | 'late' | 'dueAt'>>;
type QueueKind = (typeof QUEUE_KINDS)[number];
/** «Далее в очереди» on phones (Stitch grading-mobile). */
const UP_NEXT_COUNT = 3;

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
  const router = useRouter();
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

  const saveAndNext = async () => {
    if (!current) return;
    const nextId = entries[index + 1]?.id ?? entries[index - 1]?.id ?? null;
    const score = draft.score === '' ? null : Number(draft.score);
    if (current.kind === 'essay') {
      const parsed = parseEssayId(current.id);
      if (!parsed || score === null) return;
      setEssaySaving(true);
      try {
        await quizApi.gradeEssay(parsed.attemptId, parsed.slot, {
          score,
          comment: draft.comment || undefined,
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
          feedback: isDocEmpty(draft.feedback) ? undefined : draft.feedback,
          returnForRevision: draft.returnForRevision || undefined,
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
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button variant="ghost" size="sm" onClick={() => router.push(ROUTES.grading)}>
          <ChevronLeft aria-hidden /> {t('backToInbox')}
        </Button>
        <span className="text-sm text-text-muted" aria-live="polite">
          {t('position', { current: index + 1, total: entries.length })}
        </span>
        <div className="ml-auto flex gap-1">
          <Button
            variant="secondary"
            size="icon-sm"
            aria-label={t('shortcuts.previous')}
            disabled={index === 0}
            onClick={() => go(-1)}
          >
            <ChevronLeft aria-hidden />
          </Button>
          <Button
            variant="secondary"
            size="icon-sm"
            aria-label={t('shortcuts.next')}
            disabled={index >= entries.length - 1}
            onClick={() => go(1)}
          >
            <ChevronRight aria-hidden />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t('shortcutsTitle')}
            onClick={() => setHelpOpen(true)}
          >
            <Keyboard aria-hidden />
          </Button>
        </div>
      </div>
      <div className="grid grid-cols-1 items-start gap-gutter lg:grid-cols-[18rem_minmax(0,1fr)_22rem]">
        <nav
          aria-label={t('queue')}
          className="hidden flex-col gap-3 rounded-md border border-card-border bg-surface p-3 shadow-sm lg:sticky lg:top-[calc(var(--size-header)+1rem)] lg:flex lg:max-h-[calc(100dvh-var(--size-header)-2rem)]"
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
              className="h-10 pl-10"
            />
          </div>
          <div className="flex flex-wrap gap-1.5" role="group" aria-label={t('filterType')}>
            {(['all', ...QUEUE_KINDS] as const).map((kind) => (
              <button
                key={kind}
                type="button"
                aria-pressed={kindFilter === kind}
                onClick={() => setKindFilter(kind)}
                className={cn(
                  'h-7 rounded-full px-3 text-label-md transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                  kindFilter === kind
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-surface-muted text-text-muted hover:text-primary',
                )}
              >
                {kind === 'all' ? t('allKinds', { count: entries.length }) : t(`kinds.${kind}`)}
              </button>
            ))}
          </div>
          <ul className="-mx-1 flex flex-col gap-2 overflow-y-auto px-1 pb-1">
            {listed.map((entry) => (
              <li key={entry.id}>
                <QueueCard
                  entry={{
                    ...entry,
                    userName: entry.userName || submission.data?.userName || '',
                  }}
                  active={entry.id === current.id}
                  onSelect={() => setCurrentId(entry.id)}
                />
              </li>
            ))}
          </ul>
        </nav>
        <div className="flex min-w-0 flex-col gap-4">
          <section className="flex flex-wrap items-center gap-4 rounded-md border border-card-border bg-surface p-5 shadow-sm">
            <Avatar name={studentName || '…'} size="lg" />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <h1 className="truncate text-xl">{studentName}</h1>
              <p className="truncate text-sm text-text-muted">{current.itemTitle}</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {'submittedAt' in current && current.submittedAt ? (
                <Badge tone="neutral">
                  {t('submitted', { when: formatRelative(current.submittedAt, locale) })}
                </Badge>
              ) : null}
              {'late' in current && current.late ? (
                <Badge tone="danger" dot>
                  {t('late')}
                </Badge>
              ) : null}
              <DueLabel dueAt={'dueAt' in current ? (current.dueAt ?? null) : null} />
            </div>
          </section>
          <section
            aria-label={t('work')}
            className="min-w-0 rounded-md border border-card-border bg-surface p-5 shadow-sm md:p-6"
          >
            {current.kind === 'submission' ? (
              <SubmissionPreview id={current.id} />
            ) : (
              <EssayPreview id={current.id} />
            )}
          </section>
        </div>
        <div className="flex flex-col gap-4 lg:sticky lg:top-[calc(var(--size-header)+1rem)]">
          <aside
            aria-label={t('grade')}
            className="rounded-md border border-card-border bg-surface p-5 shadow-sm"
          >
            <GradePanel
              key={current.id}
              kind={current.kind}
              maxScore={maxScore}
              draft={draft}
              onChange={setDraft}
              saving={grade.isPending || essaySaving}
              onSave={() => void saveAndNext().catch(() => undefined)}
            />
          </aside>
          {upNext.length > 0 ? (
            <section aria-label={t('upNext')} className="flex flex-col gap-2 lg:hidden">
              <h2 className="text-lg">{t('upNext')}</h2>
              <ul className="flex flex-col gap-2">
                {upNext.map((entry) => (
                  <li key={entry.id}>
                    <QueueCard entry={entry} onSelect={() => setCurrentId(entry.id)} chevron />
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      </div>
      <ShortcutsHelp open={helpOpen} onOpenChange={setHelpOpen} />
    </div>
  );
}

/** Grading screen (FR-ASSIGN-05, AC-8): list | work | grade, J/K, Ctrl+Enter, `?`. */
export default function GradingReviewPage() {
  return (
    <Suspense>
      <Review />
    </Suspense>
  );
}
