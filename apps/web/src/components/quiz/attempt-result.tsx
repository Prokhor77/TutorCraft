'use client';
import { CheckCircle2, CircleHelp, XCircle } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import { describeResponse } from '@/features/quiz/responses';
import { useAttempt, useAttemptResult } from '@/features/quiz/use-quiz';
import { formatPercent, formatScore } from '@/lib/utils/format';

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

  return (
    <div className="mx-auto flex max-w-prose flex-col gap-6">
      <Card className="flex flex-col items-center gap-2 p-6 text-center">
        <h1 className="text-2xl">{t('resultTitle')}</h1>
        {data.score !== null ? (
          <p className="text-4xl font-bold">{formatScore(data.score, data.maxScore, locale)}</p>
        ) : (
          <p className="text-text-muted">{t('scoreHidden')}</p>
        )}
        {data.percent !== null ? (
          <p className="text-text-muted">{formatPercent(data.percent, locale)}</p>
        ) : null}
        {data.passed !== null ? (
          <Badge tone={data.passed ? 'success' : 'danger'}>
            {data.passed ? t('passed') : t('notPassed')}
          </Badge>
        ) : null}
      </Card>
      {data.needsManualGrading ? <Alert tone="info" title={t('manualGrading')} /> : null}
      <ol className="flex flex-col gap-3">
        {data.questions.map((question, index) => (
          <li key={question.slot}>
            <Card className="flex flex-col gap-2 p-4">
              <div className="flex items-start justify-between gap-3">
                <h2 className="text-base">
                  {index + 1}. {question.title}
                </h2>
                <span className="flex shrink-0 items-center gap-1.5 text-sm">
                  {question.correct === true ? (
                    <CheckCircle2 className="size-4 text-success" aria-label={t('correct')} />
                  ) : question.correct === false ? (
                    <XCircle className="size-4 text-danger" aria-label={t('incorrect')} />
                  ) : (
                    <CircleHelp className="size-4 text-text-muted" aria-hidden />
                  )}
                  {question.score !== null
                    ? formatScore(question.score, question.points, locale)
                    : null}
                </span>
              </div>
              <p className="text-sm">
                <span className="text-text-muted">{t('yourAnswer')}: </span>
                {describeResponse(question.response, questionView(question.slot), labels)}
              </p>
              {question.correctResponse ? (
                <p className="text-sm">
                  <span className="text-text-muted">{t('correctAnswer')}: </span>
                  {describeResponse(question.correctResponse, questionView(question.slot), labels)}
                </p>
              ) : null}
              {question.feedback ? (
                <p className="rounded-md bg-surface-muted p-2 text-sm">{question.feedback}</p>
              ) : null}
            </Card>
          </li>
        ))}
      </ol>
      <Button asChild variant="secondary" className="self-center">
        <Link href={ROUTES.item(courseId, itemId)}>{t('backToQuiz')}</Link>
      </Button>
    </div>
  );
}
