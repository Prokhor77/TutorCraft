'use client';
import { ArrowLeft, CheckCircle2, CircleHelp, MessageSquareText, XCircle } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import { describeResponse } from '@/features/quiz/responses';
import { useAttempt, useAttemptResult } from '@/features/quiz/use-quiz';
import { cn } from '@/lib/utils/cn';
import { formatPercent, formatScore } from '@/lib/utils/format';

const RING_RADIUS = 52;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;
const PERCENT_MAX = 100;

/** Decorative score ring; the percent itself is rendered as text next to it. */
function ScoreRing({ percent, passed }: { percent: number; passed: boolean | null }) {
  const clamped = Math.min(PERCENT_MAX, Math.max(0, percent));
  return (
    <svg viewBox="0 0 120 120" className="size-32 shrink-0 -rotate-90" aria-hidden>
      <circle
        cx="60"
        cy="60"
        r={RING_RADIUS}
        fill="none"
        strokeWidth="10"
        className="stroke-surface-muted"
      />
      <circle
        cx="60"
        cy="60"
        r={RING_RADIUS}
        fill="none"
        strokeWidth="10"
        strokeLinecap="round"
        strokeDasharray={RING_LENGTH}
        strokeDashoffset={RING_LENGTH * (1 - clamped / PERCENT_MAX)}
        className={cn(
          'transition-[stroke-dashoffset] duration-base',
          passed === false ? 'stroke-danger' : passed ? 'stroke-success' : 'stroke-primary',
        )}
      />
    </svg>
  );
}

function CountChip({
  icon: Icon,
  label,
  count,
  tone,
}: {
  icon: typeof CheckCircle2;
  label: string;
  count: number;
  tone: 'success' | 'danger' | 'neutral';
}) {
  return (
    <li
      className={cn(
        'flex items-center gap-2 rounded-full px-3.5 py-1.5 text-label-lg',
        tone === 'success' && 'bg-success-soft text-success',
        tone === 'danger' && 'bg-danger-soft text-danger',
        tone === 'neutral' && 'bg-surface-muted text-text-muted',
      )}
    >
      <Icon className="size-4" aria-hidden /> {label}: {count}
    </li>
  );
}

/** Results per review rules (FR-QUIZ-05): the server decides which fields are present. */
export function AttemptResultView({
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
  const locale = useLocale();
  const result = useAttemptResult(attemptId);
  const attempt = useAttempt(attemptId);
  const labels = { true: t('true'), false: t('false') };

  if (result.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (result.isError || !result.data)
    return (
      <ErrorState
        title={t('resultLoadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void result.refetch()}
      />
    );
  const data = result.data;
  const questionView = (slot: number) =>
    attempt.data?.questions.find((question) => question.slot === slot);
  const correct = data.questions.filter((question) => question.correct === true).length;
  const incorrect = data.questions.filter((question) => question.correct === false).length;
  const pending = data.questions.length - correct - incorrect;
  const graded = correct + incorrect > 0;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-gutter">
      <section
        aria-labelledby="result-title"
        aria-live="polite"
        className="flex flex-col items-center gap-5 rounded-lg border border-card-border bg-surface p-6 text-center shadow-sm sm:flex-row sm:p-8 sm:text-left"
      >
        {data.percent !== null ? (
          <div className="relative flex items-center justify-center">
            <ScoreRing percent={data.percent} passed={data.passed} />
            <span className="absolute font-heading text-2xl font-bold">
              {formatPercent(data.percent, locale)}
            </span>
          </div>
        ) : null}
        <div className="flex min-w-0 flex-1 flex-col items-center gap-2 sm:items-start">
          <span className="text-label-md uppercase text-text-muted">{t('resultEyebrow')}</span>
          <h1 id="result-title" className="text-2xl md:text-3xl">
            {t('resultTitle')}
          </h1>
          {data.score !== null ? (
            <p className="flex items-baseline gap-2">
              <span className="font-heading text-4xl font-bold tracking-tight">
                {formatScore(data.score, data.maxScore, locale)}
              </span>
              <span className="text-sm text-text-muted">{t('scoreUnit')}</span>
            </p>
          ) : (
            <p className="text-text-muted">{t('scoreHidden')}</p>
          )}
          {data.passed !== null ? (
            <Badge tone={data.passed ? 'success' : 'danger'} dot>
              {data.passed ? t('passed') : t('notPassed')}
            </Badge>
          ) : null}
          {graded ? (
            <ul className="mt-2 flex flex-wrap justify-center gap-2 sm:justify-start">
              <CountChip icon={CheckCircle2} tone="success" label={t('correct')} count={correct} />
              <CountChip icon={XCircle} tone="danger" label={t('incorrect')} count={incorrect} />
              {pending > 0 ? (
                <CountChip icon={CircleHelp} tone="neutral" label={t('pending')} count={pending} />
              ) : null}
            </ul>
          ) : null}
        </div>
      </section>
      {data.needsManualGrading ? <Alert tone="info" title={t('manualGrading')} /> : null}
      <ol className="flex flex-col gap-3" aria-label={t('answersTitle')}>
        {data.questions.map((question, index) => {
          const view = questionView(question.slot);
          return (
            <li
              key={question.slot}
              className="flex flex-col gap-3 rounded-lg border border-card-border bg-surface p-5 shadow-sm sm:p-6"
            >
              <div className="flex items-start gap-3">
                <span
                  className={cn(
                    'flex size-9 shrink-0 items-center justify-center rounded-full font-heading text-base font-bold',
                    question.correct === true && 'bg-success-soft text-success',
                    question.correct === false && 'bg-danger-soft text-danger',
                    question.correct === null && 'bg-surface-muted text-text-muted',
                  )}
                >
                  {index + 1}
                </span>
                <h2 className="min-w-0 flex-1 pt-1 text-lg">{question.title}</h2>
                <span
                  className={cn(
                    'flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1 text-label-md',
                    question.correct === true && 'bg-success-soft text-success',
                    question.correct === false && 'bg-danger-soft text-danger',
                    question.correct === null && 'bg-surface-muted text-text-muted',
                  )}
                >
                  {question.correct === true ? (
                    <CheckCircle2 className="size-4" aria-label={t('correct')} />
                  ) : question.correct === false ? (
                    <XCircle className="size-4" aria-label={t('incorrect')} />
                  ) : (
                    <CircleHelp className="size-4" aria-hidden />
                  )}
                  {question.score !== null
                    ? formatScore(question.score, question.points, locale)
                    : null}
                </span>
              </div>
              <dl className="flex flex-col gap-2 sm:pl-12">
                <div
                  className={cn(
                    'flex flex-col gap-0.5 rounded px-4 py-3',
                    question.correct === false && 'bg-danger-soft/60',
                    question.correct === true && 'bg-success-soft',
                    question.correct === null && 'bg-surface-muted',
                  )}
                >
                  <dt className="text-label-md uppercase text-text-muted">{t('yourAnswer')}</dt>
                  <dd className="text-sm">{describeResponse(question.response, view, labels)}</dd>
                </div>
                {question.correctResponse ? (
                  <div className="flex flex-col gap-0.5 rounded bg-success-soft px-4 py-3">
                    <dt className="text-label-md uppercase text-success">{t('correctAnswer')}</dt>
                    <dd className="text-sm">
                      {describeResponse(question.correctResponse, view, labels)}
                    </dd>
                  </div>
                ) : null}
                {question.feedback ? (
                  <div className="rounded bg-primary-soft/60 px-4 py-3 text-sm">
                    <dt className="sr-only">{t('feedback')}</dt>
                    <dd className="flex gap-2.5">
                      <MessageSquareText
                        className="mt-0.5 size-4 shrink-0 text-primary"
                        aria-hidden
                      />
                      {question.feedback}
                    </dd>
                  </div>
                ) : null}
              </dl>
            </li>
          );
        })}
      </ol>
      <Button asChild variant="secondary" className="self-center">
        <Link href={ROUTES.item(courseId, itemId)}>
          <ArrowLeft aria-hidden /> {t('backToQuiz')}
        </Link>
      </Button>
    </div>
  );
}
