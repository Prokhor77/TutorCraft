'use client';
import { FileText } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { FileCard, FilePreview } from '@/components/media/file-preview';
import { StatusBadge } from '@/components/course/item-meta';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { useSubmission } from '@/features/assessment/use-assessment';
import { parseEssayId } from '@/features/assessment/quick-comments';
import { useAttempt } from '@/features/quiz/use-quiz';
import { essayText } from '@/features/quiz/responses';
import type { FileMeta } from '@/lib/api/schemas/files';
import { isDocEmpty } from '@/lib/blockdoc/doc';
import { cn } from '@/lib/utils/cn';
import { formatDateTime } from '@/lib/utils/format';

const INLINE_MIME_PREFIXES = ['image/', 'audio/', 'video/'];

/** Files the browser can show inline (the rest are offered as a download card only). */
function isInlinePreviewable(meta: FileMeta): boolean {
  return (
    !!meta.video ||
    meta.mime === 'application/pdf' ||
    INLINE_MIME_PREFIXES.some((prefix) => meta.mime.startsWith(prefix))
  );
}

/**
 * Stitch submission canvas: meta row (status · time · attempt), the answer text on a white sheet inside a tinted
 * stage, and one pill per attached file («Скан 1», «Скан 2») switching the inline preview.
 */
export function SubmissionPreview({ id }: { id: string }) {
  const t = useTranslations('grading');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const submission = useSubmission(id);
  const [fileIndex, setFileIndex] = useState(0);
  if (submission.isLoading) return <SkeletonList label={tCommon('loading')} rows={3} />;
  if (submission.isError || !submission.data) return <ErrorState title={t('previewError')} />;
  const data = submission.data;
  const selected = data.files[Math.min(fileIndex, data.files.length - 1)];
  const hasText = !!data.text && !isDocEmpty(data.text);
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2 text-xs text-text-muted">
        <StatusBadge status={data.status} />
        {data.submittedAt ? <span>{formatDateTime(data.submittedAt, locale)}</span> : null}
        {data.late ? <span className="font-semibold text-danger">{t('late')}</span> : null}
        <span className="ml-auto rounded-full bg-surface-muted px-2.5 py-0.5 text-label-md">
          {t('attemptNo', { number: data.attemptNo })}
        </span>
      </div>
      {hasText || selected ? (
        <div className="flex flex-col gap-3 rounded-md bg-surface-muted p-3 sm:p-4">
          {hasText && data.text ? (
            <div className="rounded-md bg-surface p-4 shadow-sm sm:p-5">
              <BlockRenderer doc={data.text} />
            </div>
          ) : null}
          {selected ? (
            <div className="flex flex-col gap-2">
              {isInlinePreviewable(selected) ? (
                <div className="overflow-hidden rounded-md bg-surface p-2 shadow-sm">
                  <FilePreview meta={selected} />
                </div>
              ) : null}
              <FileCard meta={selected} />
            </div>
          ) : null}
        </div>
      ) : (
        <p className="rounded-md bg-surface-muted p-4 text-sm text-text-muted">
          {t('emptySubmission')}
        </p>
      )}
      {data.files.length > 1 ? (
        <div role="group" aria-label={t('files')} className="flex flex-wrap gap-2">
          {data.files.map((file, index) => (
            <button
              key={file.id}
              type="button"
              aria-pressed={file.id === selected?.id}
              onClick={() => setFileIndex(index)}
              className={cn(
                'inline-flex h-8 max-w-60 items-center gap-1.5 rounded-full px-3 text-label-md transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                file.id === selected?.id
                  ? 'bg-primary-soft text-primary'
                  : 'bg-surface-muted text-text-muted hover:text-primary',
              )}
            >
              <FileText className="size-3.5 shrink-0" aria-hidden />
              <span className="truncate">{file.name}</span>
            </button>
          ))}
        </div>
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
      <div className="rounded-md bg-surface-muted p-3 sm:p-4">
        <div className="whitespace-pre-wrap rounded-md bg-surface p-4 text-sm leading-relaxed shadow-sm sm:p-5">
          {essayText(question.response) || t('emptySubmission')}
        </div>
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
