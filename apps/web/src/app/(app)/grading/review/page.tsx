'use client';
import { ChevronLeft, ChevronRight, Inbox, Keyboard } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { Suspense, useEffect, useMemo, useState } from 'react';
import { EssayPreview, SubmissionPreview } from '@/components/grading/submission-preview';
import { emptyGradeDraft, GradePanel, type GradeDraft } from '@/components/grading/grade-panel';
import { ShortcutsHelp } from '@/components/grading/shortcuts-help';
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
import type { QueueEntry } from '@/lib/api/schemas/assessment';
import { isDocEmpty } from '@/lib/blockdoc/doc';
import { cn } from '@/lib/utils/cn';

type ReviewEntry = Pick<QueueEntry, 'kind' | 'id' | 'itemId' | 'userName' | 'itemTitle'>;

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
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[16rem_minmax(0,1fr)_22rem]">
        <nav
          aria-label={t('queue')}
          className="hidden max-h-[calc(100dvh-12rem)] overflow-y-auto rounded-lg border border-border bg-surface p-2 lg:block"
        >
          <ul className="flex flex-col gap-0.5">
            {entries.map((entry) => (
              <li key={entry.id}>
                <button
                  type="button"
                  onClick={() => setCurrentId(entry.id)}
                  aria-current={entry.id === current.id ? 'true' : undefined}
                  className={cn(
                    'flex w-full flex-col rounded-md px-2 py-2 text-left text-sm hover:bg-surface-muted',
                    entry.id === current.id && 'bg-primary-soft text-primary',
                  )}
                >
                  <span className="truncate font-medium">
                    {entry.userName || submission.data?.userName || '…'}
                  </span>
                  <span className="truncate text-xs text-text-muted">{entry.itemTitle}</span>
                </button>
              </li>
            ))}
          </ul>
        </nav>
        <section
          aria-label={t('work')}
          className="min-w-0 rounded-lg border border-border bg-surface p-4"
        >
          <h1 className="mb-3 text-lg">
            {current.userName || submission.data?.userName}{' '}
            <span className="text-text-muted">· {current.itemTitle}</span>
          </h1>
          {current.kind === 'submission' ? (
            <SubmissionPreview id={current.id} />
          ) : (
            <EssayPreview id={current.id} />
          )}
        </section>
        <aside
          aria-label={t('grade')}
          className="rounded-lg border border-border bg-surface p-4 lg:sticky lg:top-[calc(var(--size-header)+1rem)] lg:self-start"
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
