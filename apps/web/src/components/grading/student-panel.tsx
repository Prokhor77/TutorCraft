'use client';
import { useLocale, useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { StatusBadge } from '@/components/course/item-meta';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Panel } from '@/components/ui/page-header';
import type { QueueEntry, Submission } from '@/lib/api/schemas/assessment';
import { formatDateTime } from '@/lib/utils/format';

export type StudentPanelEntry = Pick<QueueEntry, 'kind' | 'userName' | 'itemTitle'> &
  Partial<Pick<QueueEntry, 'courseTitle' | 'submittedAt' | 'dueAt' | 'late'>>;

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 py-2 text-xs">
      <dt className="shrink-0 text-text-muted">{label}</dt>
      <dd className="min-w-0 text-right font-semibold">{children}</dd>
    </div>
  );
}

/**
 * Right column of the review workbench (Stitch «Профиль ученика»): avatar and name, the work's facts from the queue
 * entry and submission (course, item, submitted / due, attempt) and the attempt history with scores.
 */
export function StudentPanel({
  entry,
  submission,
}: {
  entry: StudentPanelEntry;
  submission?: Submission;
}) {
  const t = useTranslations('grading');
  const locale = useLocale();
  const name = entry.userName || submission?.userName || '…';
  const submittedAt = entry.submittedAt ?? submission?.submittedAt ?? null;
  const dueAt = entry.dueAt ?? submission?.dueAt ?? null;
  const late = entry.late ?? submission?.late ?? false;
  const history = submission?.history ?? [];
  const grade = submission?.grade;
  return (
    <Panel
      as="aside"
      title={<span className="text-base">{t('studentProfile')}</span>}
      actions={
        submittedAt ? (
          <Badge tone={late ? 'danger' : 'success'} dot>
            {late ? t('late') : t('onTime')}
          </Badge>
        ) : null
      }
    >
      <div className="flex flex-col items-center gap-2 text-center">
        <Avatar name={name} size="lg" className="size-20 text-2xl ring-4 ring-surface-muted" />
        <p className="font-heading text-lg font-semibold">{name}</p>
        {entry.courseTitle ? <p className="text-xs text-text-muted">{entry.courseTitle}</p> : null}
      </div>
      <dl className="divide-y divide-border rounded-md bg-surface-muted px-4 py-1">
        <Row label={t('kindLabel')}>{t(`kinds.${entry.kind}`)}</Row>
        {entry.itemTitle ? <Row label={t('workLabel')}>{entry.itemTitle}</Row> : null}
        {submittedAt ? (
          <Row label={t('submittedLabel')}>{formatDateTime(submittedAt, locale)}</Row>
        ) : null}
        <Row label={t('dueLabel')}>{dueAt ? formatDateTime(dueAt, locale) : t('noDue')}</Row>
        {submission ? <Row label={t('attemptLabel')}>{submission.attemptNo}</Row> : null}
        {grade && grade.score !== null ? (
          <Row label={t('currentGrade')}>
            {grade.score} / {grade.maxScore}
            {grade.graderName ? (
              <span className="block font-normal text-text-muted">{grade.graderName}</span>
            ) : null}
          </Row>
        ) : null}
      </dl>
      {history.length > 0 ? (
        <section className="flex flex-col gap-2" aria-label={t('attemptHistory')}>
          <h3 className="text-sm">{t('attemptHistory')}</h3>
          <ul className="flex flex-col gap-1.5">
            {history.map((attempt) => (
              <li
                key={attempt.attemptNo}
                className="flex items-center justify-between gap-2 rounded-full bg-surface-muted py-1 pl-3 pr-1 text-xs"
              >
                <span className="font-semibold">
                  {t('attemptNo', { number: attempt.attemptNo })}
                </span>
                <span className="flex items-center gap-1.5">
                  {attempt.score !== null ? (
                    <span className="font-semibold tabular-nums">{attempt.score}</span>
                  ) : null}
                  <StatusBadge status={attempt.status} />
                </span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </Panel>
  );
}
