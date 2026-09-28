'use client';
import { Flag, CheckCircle2, CloudOff, Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useRef, useState } from 'react';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import { useOnlineStatus } from '@/features/offline/use-offline-runner';
import { isAnswered } from '@/features/quiz/responses';
import {
  useAnswerSaver,
  useAttempt,
  useAttemptTimer,
  useFinishAttempt,
  type SlotSaveState,
} from '@/features/quiz/use-quiz';
import type { QuestionResponse, StudentQuestionView } from '@/lib/api/schemas/quiz';
import { cn } from '@/lib/utils/cn';
import { QuestionInput } from './question-input';
import { QuestionTypeTag, PointsPill } from './question-type';
import { QuizTimer } from './quiz-timer';

type LocalAnswers = Record<number, { response: QuestionResponse | null; flagged: boolean }>;

function SaveState({ state }: { state: SlotSaveState | undefined }) {
  const t = useTranslations('quiz');
  if (!state) return null;
  const icon =
    state === 'saving' ? (
      <Loader2 className="size-3.5 animate-spin" aria-hidden />
    ) : state === 'saved' ? (
      <CheckCircle2 className="size-3.5" aria-hidden />
    ) : (
      <CloudOff className="size-3.5" aria-hidden />
    );
  return (
    <span
      role="status"
      className={cn(
        'inline-flex items-center gap-1 text-xs',
        state === 'saved'
          ? 'text-success'
          : state === 'saving'
            ? 'text-text-muted'
            : 'text-warning',
      )}
    >
      {icon} {t(`save.${state}`)}
    </span>
  );
}

function Navigator({
  questions,
  answers,
  current,
  onJump,
}: {
  questions: StudentQuestionView[];
  answers: LocalAnswers;
  current: number;
  onJump: (page: number, slot: number) => void;
}) {
  const t = useTranslations('quiz');
  return (
    <nav aria-label={t('navigator')} className="flex flex-wrap gap-1.5">
      {questions.map((question, index) => {
        const answer = answers[question.slot];
        const answered = isAnswered(answer?.response);
        return (
          <button
            key={question.slot}
            type="button"
            onClick={() => onJump(question.page, question.slot)}
            aria-current={question.page === current ? 'step' : undefined}
            aria-label={t('jumpTo', {
              number: index + 1,
              state: answered ? t('answered') : t('notAnswered'),
            })}
            className={cn(
              'relative flex size-9 items-center justify-center rounded-full border-[1.5px] text-sm font-semibold transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
              answered
                ? 'border-primary bg-primary text-primary-foreground'
                : 'border-border bg-surface',
              question.page === current && 'ring-2 ring-primary ring-offset-1',
            )}
          >
            {index + 1}
            {answer?.flagged ? (
              <Flag
                className="absolute -right-1 -top-1 size-3 fill-warning text-warning"
                aria-hidden
              />
            ) : null}
          </button>
        );
      })}
    </nav>
  );
}

/** Quiz attempt (FR-QUIZ-03, AC-4): per-answer autosave with retry queue, server-synced timer, navigator, flags. */
export function AttemptRunner({
  courseId,
  itemId,
  attemptId,
}: {
  courseId: string;
  itemId: string;
  attemptId: string;
}) {
  const t = useTranslations('quiz');
  const tCommon = useTranslations('common');
  const router = useRouter();
  const online = useOnlineStatus();
  const attempt = useAttempt(attemptId);
  const fetchedAt = useRef(Date.now());
  const [answers, setAnswers] = useState<LocalAnswers>({});
  const [page, setPage] = useState(0);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const finish = useFinishAttempt(attemptId);
  const resultHref = ROUTES.attemptResult(courseId, itemId, attemptId);
  const saver = useAnswerSaver(attemptId, () => router.replace(resultHref));
  const finishNow = async () => {
    await saver.flushAll();
    finish.mutate(undefined, {
      onSuccess: () => router.replace(resultHref),
      onError: () => router.replace(resultHref),
    });
  };
  const remaining = useAttemptTimer(attempt.data, fetchedAt.current);
  const autoFinished = useRef(false);

  useEffect(() => {
    if (!attempt.data) return;
    fetchedAt.current = Date.now();
    if (attempt.data.state !== 'in_progress') return router.replace(resultHref);
    setAnswers(
      Object.fromEntries(
        attempt.data.questions.map((question) => [
          question.slot,
          { response: question.response, flagged: question.flagged },
        ]),
      ),
    );
    setPage(attempt.data.questions[0]?.page ?? 0);
  }, [attempt.data, router, resultHref]);

  useEffect(() => {
    if (remaining !== 0 || autoFinished.current) return;
    autoFinished.current = true;
    void finishNow();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- fire once when the server-synced timer hits zero
  }, [remaining]);

  if (attempt.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (attempt.isError || !attempt.data)
    return (
      <ErrorState
        title={t('attemptLoadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void attempt.refetch()}
      />
    );
  const questions = attempt.data.questions;
  const pages = [...new Set(questions.map((question) => question.page))].sort((a, b) => a - b);
  const pageIndex = pages.indexOf(page);
  const visible = questions.filter((question) => question.page === page);
  const unanswered = questions.filter(
    (question) => !isAnswered(answers[question.slot]?.response),
  ).length;

  const update = (question: StudentQuestionView, response: QuestionResponse, debounce: boolean) => {
    const flagged = answers[question.slot]?.flagged ?? false;
    setAnswers((current) => ({ ...current, [question.slot]: { response, flagged } }));
    saver.save(question.slot, response, { flagged, debounce });
  };
  const toggleFlag = (question: StudentQuestionView) => {
    const entry = answers[question.slot] ?? { response: null, flagged: false };
    const next = { ...entry, flagged: !entry.flagged };
    setAnswers((current) => ({ ...current, [question.slot]: next }));
    if (next.response) saver.save(question.slot, next.response, { flagged: next.flagged });
  };

  return (
    <div className="mx-auto flex max-w-prose flex-col gap-6">
      <div className="glass sticky top-header z-10 -mx-page-x flex flex-col gap-3 border-b border-border px-page-x py-3">
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm text-text-muted">
            {t('pageOf', { page: pageIndex + 1, total: pages.length })}
          </span>
          <QuizTimer remaining={remaining} />
        </div>
        <Navigator
          questions={questions}
          answers={answers}
          current={page}
          onJump={(target) => setPage(target)}
        />
      </div>
      {!online ? (
        <Alert tone="warning" title={t('offlineTitle')}>
          {t('offlineText')}
        </Alert>
      ) : null}
      {visible.map((question) => {
        const index = questions.indexOf(question);
        const entry = answers[question.slot];
        return (
          <section
            key={question.slot}
            aria-labelledby={`question-${question.slot}`}
            className="flex flex-col gap-4 rounded-lg border border-card-border bg-surface p-6 shadow-sm transition-shadow duration-fast focus-within:shadow-md md:p-8"
          >
            <header className="flex flex-wrap items-start justify-between gap-2">
              <div className="flex min-w-0 flex-1 basis-56 flex-col gap-2">
                <span className="flex flex-wrap items-center gap-2">
                  <span className="text-label-md uppercase text-text-muted">
                    {t('questionOf', { number: index + 1, total: questions.length })}
                  </span>
                  <QuestionTypeTag type={question.type} />
                  <PointsPill points={question.points} />
                </span>
                <h2 id={`question-${question.slot}`} className="text-xl">
                  {question.title}
                </h2>
              </div>
              <Button
                variant={entry?.flagged ? 'soft' : 'ghost'}
                size="sm"
                aria-pressed={entry?.flagged ?? false}
                onClick={() => toggleFlag(question)}
              >
                <Flag aria-hidden /> {entry?.flagged ? t('flagged') : t('flag')}
              </Button>
            </header>
            <BlockRenderer doc={question.body} />
            <QuestionInput
              question={question}
              response={entry?.response ?? null}
              onChange={(response, debounce) => update(question, response, debounce)}
              disabled={finish.isPending}
            />
            <div className="flex justify-end">
              <SaveState state={saver.states[question.slot]} />
            </div>
          </section>
        );
      })}
      <div className="flex items-center justify-between gap-3 pb-4">
        <Button
          variant="secondary"
          disabled={pageIndex <= 0}
          onClick={() => setPage(pages[pageIndex - 1] ?? page)}
        >
          {t('previous')}
        </Button>
        {pageIndex < pages.length - 1 ? (
          <Button onClick={() => setPage(pages[pageIndex + 1] ?? page)}>{t('next')}</Button>
        ) : (
          <Button onClick={() => setConfirmOpen(true)}>{t('finish')}</Button>
        )}
      </div>
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent
          title={t('finishTitle')}
          description={
            unanswered > 0 ? t('unansweredWarning', { count: unanswered }) : t('finishHint')
          }
          closeLabel={tCommon('close')}
        >
          <DialogFooter>
            <Button variant="secondary" onClick={() => setConfirmOpen(false)}>
              {t('backToAttempt')}
            </Button>
            <Button loading={finish.isPending} onClick={() => void finishNow()}>
              {t('finishConfirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
