'use client';
import { useLocale, useTranslations } from 'next-intl';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { FileCard, FilePreview } from '@/components/media/file-preview';
import { StatusBadge } from '@/components/course/item-meta';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { useSubmission } from '@/features/assessment/use-assessment';
import { parseEssayId } from '@/features/assessment/quick-comments';
import { useAttempt } from '@/features/quiz/use-quiz';
import { essayText } from '@/features/quiz/responses';
import { formatDateTime } from '@/lib/utils/format';

export function SubmissionPreview({ id }: { id: string }) {
  const t = useTranslations('grading');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const submission = useSubmission(id);
  if (submission.isLoading) return <SkeletonList label={tCommon('loading')} rows={3} />;
  if (submission.isError || !submission.data) return <ErrorState title={t('previewError')} />;
  const data = submission.data;
  const [first, ...rest] = data.files;
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <StatusBadge status={data.status} />
        {data.submittedAt ? (
          <span className="text-text-muted">{formatDateTime(data.submittedAt, locale)}</span>
        ) : null}
        {data.late ? <span className="text-warning">{t('late')}</span> : null}
        <span className="text-text-muted">{t('attemptNo', { number: data.attemptNo })}</span>
      </div>
      <BlockRenderer doc={data.text} />
      {first ? <FilePreview meta={first} /> : null}
      {data.files.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {[first, ...rest].filter(Boolean).map((file) =>
            file ? (
              <li key={file.id}>
                <FileCard meta={file} />
              </li>
            ) : null,
          )}
        </ul>
      ) : null}
      {!data.text && data.files.length === 0 ? (
        <p className="text-sm text-text-muted">{t('emptySubmission')}</p>
      ) : null}
    </div>
  );
}

export function EssayPreview({ id }: { id: string }) {
  const t = useTranslations('grading');
  const tCommon = useTranslations('common');
  const parsed = parseEssayId(id);
  const attempt = useAttempt(parsed?.attemptId ?? '');
  if (!parsed) return <ErrorState title={t('previewError')} />;
  if (attempt.isLoading) return <SkeletonList label={tCommon('loading')} rows={3} />;
  const question = attempt.data?.questions.find((entry) => entry.slot === parsed.slot);
  if (!question) return <ErrorState title={t('previewError')} />;
  return (
    <div className="flex flex-col gap-3">
      <h3 className="text-base">{question.title}</h3>
      <BlockRenderer doc={question.body} />
      <div className="whitespace-pre-wrap rounded-md border border-border bg-surface-muted p-4 text-sm leading-relaxed">
        {essayText(question.response) || t('emptySubmission')}
      </div>
    </div>
  );
}

export function essayPoints(
  attemptQuestions: { slot: number; points: number }[] | undefined,
  id: string,
): number | null {
  const parsed = parseEssayId(id);
  return attemptQuestions?.find((question) => question.slot === parsed?.slot)?.points ?? null;
}
