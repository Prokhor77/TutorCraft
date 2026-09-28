'use client';
import { Flag, CheckCircle2, CloudOff, Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useRef, useState } from 'react';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
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
    <nav aria-label={t('navigator')} className="flex flex-wrap gap-2">
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
              'relative flex size-10 items-center justify-center rounded-full border-[1.5px] text-sm font-semibold transition-[background-color,border-color,box-shadow] duration-fast hover:border-accent/60 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
              answered
                ? 'border-primary bg-primary text-primary-foreground'
                : 'border-outline-variant bg-surface text-text-muted',
              question.page === current &&
                'ring-2 ring-accent/30 ring-offset-1 ring-offset-surface',
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

  const answeredCount = questions.length - unanswered;
  const progressLabel = t('answeredOf', { answered: answeredCount, total: questions.length });
  const navigator = (
    <Navigator
      questions={questions}
      answers={answers}
      current={page}
      onJump={(target) => setPage(target)}
    />
  );
  const finishButton = (
    <Button className="w-full" onClick={() => setConfirmOpen(true)}>
      <CheckCircle2 aria-hidden /> {t('finish')}
    </Button>
  );

  return (
    <div className="mx-auto grid w-full max-w-6xl grid-cols-1 items-start gap-gutter lg:grid-cols-[minmax(0,1fr)_18rem]">
      <div className="flex min-w-0 flex-col gap-gutter">
        {/* Phones / tablets: sticky progress bar with timer; the navigator sits in the side panel on lg+. */}
        <div className="glass sticky top-header z-10 flex flex-col gap-3 rounded-lg border border-card-border px-4 py-3 shadow-sm lg:hidden">
          <div className="flex items-center justify-between gap-3">
            <span className="text-label-md uppercase text-text-muted">
              {t('pageOf', { page: pageIndex + 1, total: pages.length })}
            </span>
            <QuizTimer remaining={remaining} />
          </div>
          <div className="flex items-center gap-3">
            <Progress
              value={questions.length ? (answeredCount / questions.length) * 100 : 0}
              label={progressLabel}
            />
            <span className="shrink-0 text-label-md text-text-muted">
              {answeredCount}/{questions.length}
            </span>
          </div>
          {navigator}
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
              className={cn(
                'flex flex-col gap-5 rounded-lg border bg-surface p-5 shadow-sm transition-[box-shadow,border-color] duration-fast focus-within:border-card-border-hover focus-within:shadow-md sm:p-8',
                entry?.flagged ? 'border-warning/40' : 'border-card-border',
              )}
            >
              <header className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex min-w-0 flex-1 basis-56 flex-col gap-3">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary font-heading text-base font-bold text-primary-foreground">
                      {index + 1}
                    </span>
                    <span className="sr-only">
                      {t('questionOf', { number: index + 1, total: questions.length })}
                    </span>
                    <QuestionTypeTag type={question.type} />
                    <PointsPill points={question.points} />
                  </span>
                  <h2 id={`question-${question.slot}`} className="text-xl md:text-2xl">
                    {question.title}
                  </h2>
                </div>
                <Button
                  variant={entry?.flagged ? 'soft' : 'ghost'}
                  size="sm"
                  className={cn(
                    entry?.flagged && 'bg-warning-soft text-warning hover:bg-warning-soft',
                  )}
                  aria-pressed={entry?.flagged ?? false}
                  onClick={() => toggleFlag(question)}
                >
                  <Flag aria-hidden className={cn(entry?.flagged && 'fill-current')} />{' '}
                  {entry?.flagged ? t('flagged') : t('flag')}
                </Button>
              </header>
              <BlockRenderer doc={question.body} />
              <QuestionInput
                question={question}
                response={entry?.response ?? null}
                onChange={(response, debounce) => update(question, response, debounce)}
                disabled={finish.isPending}
              />
              <div className="flex min-h-5 justify-end">
                <SaveState state={saver.states[question.slot]} />
              </div>
            </section>
          );
        })}
        <div className="flex items-center justify-between gap-3 rounded-lg border border-card-border bg-surface p-3 shadow-sm">
          <Button
            variant="secondary"
            disabled={pageIndex <= 0}
            onClick={() => setPage(pages[pageIndex - 1] ?? page)}
          >
            {t('previous')}
          </Button>
          <span className="text-label-md text-text-muted">
            {t('pageOf', { page: pageIndex + 1, total: pages.length })}
          </span>
          {pageIndex < pages.length - 1 ? (
            <Button onClick={() => setPage(pages[pageIndex + 1] ?? page)}>{t('next')}</Button>
          ) : (
            <Button onClick={() => setConfirmOpen(true)}>{t('finish')}</Button>
          )}
        </div>
      </div>
      <aside
        aria-labelledby="attempt-progress-title"
        className="sticky top-[calc(var(--size-header)+1rem)] hidden flex-col gap-4 rounded-lg border border-card-border bg-surface p-5 shadow-sm lg:flex"
      >
        <div className="flex items-center justify-between gap-2">
          <h2 id="attempt-progress-title" className="text-lg">
            {t('progressTitle')}
          </h2>
          <QuizTimer remaining={remaining} />
        </div>
        <div className="flex flex-col gap-2">
          <Progress
            value={questions.length ? (answeredCount / questions.length) * 100 : 0}
            label={progressLabel}
          />
          <p className="text-xs text-text-muted">{progressLabel}</p>
        </div>
        {navigator}
        <ul className="flex flex-col gap-1.5 text-xs text-text-muted">
          <li className="flex items-center gap-2">
            <span className="size-3 rounded-full bg-primary" aria-hidden /> {t('answered')}
          </li>
          <li className="flex items-center gap-2">
            <span
              className="size-3 rounded-full border-[1.5px] border-outline-variant"
              aria-hidden
            />{' '}
            {t('notAnswered')}
          </li>
          <li className="flex items-center gap-2">
            <Flag className="size-3 fill-warning text-warning" aria-hidden /> {t('flagged')}
          </li>
        </ul>
        <div className="border-t border-border pt-4">{finishButton}</div>
      </aside>
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
