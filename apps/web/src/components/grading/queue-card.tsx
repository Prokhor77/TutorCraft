'use client';
import { AlertTriangle, ChevronRight, Hourglass } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Avatar } from '@/components/ui/avatar';
import type { QueueEntry } from '@/lib/api/schemas/assessment';
import { cn } from '@/lib/utils/cn';
import { formatRelative } from '@/lib/utils/format';

type CardEntry = Pick<QueueEntry, 'id' | 'kind' | 'userName' | 'itemTitle'> &
  Partial<Pick<QueueEntry, 'courseTitle' | 'submittedAt' | 'late'>>;

/**
 * Stitch queue card: avatar, student, work, a status line (waiting / late · kind) with the «45 мин назад» amber chip;
 * the current entry gets the tinted fill and violet accent bar. Renders a link (`href`) or a button (`onSelect`).
 */
export function QueueCard({
  entry,
  active,
  href,
  onSelect,
  showCourse,
  chevron,
}: {
  entry: CardEntry;
  active?: boolean;
  href?: string;
  onSelect?: () => void;
  showCourse?: boolean;
  chevron?: boolean;
}) {
  const t = useTranslations('grading');
  const locale = useLocale();
  const name = entry.userName || '…';
  const className = cn(
    'relative flex w-full items-start gap-3 rounded-md border p-4 text-left transition-[box-shadow,border-color,background-color] duration-fast hover:border-card-border-hover hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
    active
      ? 'border-transparent bg-surface-muted shadow-sm'
      : 'border-card-border bg-surface shadow-sm',
  );
  const content = (
    <>
      {active ? (
        <span aria-hidden className="absolute inset-y-4 left-0 w-1 rounded-r-full bg-primary" />
      ) : null}
      <Avatar name={name} size="md" className="shrink-0" />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-label-lg">{name}</span>
        <span className="truncate text-xs text-text-muted">
          {entry.itemTitle}
          {showCourse && entry.courseTitle ? ` · ${entry.courseTitle}` : ''}
        </span>
        <span className="mt-1.5 flex flex-wrap items-center justify-between gap-x-2 gap-y-1 text-xs">
          <span className="flex min-w-0 items-center gap-1">
            {entry.late ? (
              <span className="flex shrink-0 items-center gap-1 font-semibold text-danger">
                <AlertTriangle className="size-3.5" aria-hidden /> {t('late')}
              </span>
            ) : (
              <span className="flex shrink-0 items-center gap-1 font-semibold text-primary">
                <Hourglass className="size-3.5" aria-hidden /> {t('waiting')}
              </span>
            )}
            <span className="truncate text-text-muted">· {t(`kinds.${entry.kind}`)}</span>
          </span>
          {entry.submittedAt ? (
            <span className="shrink-0 rounded-full bg-warning-soft px-2 py-0.5 text-label-sm text-warning">
              {formatRelative(entry.submittedAt, locale)}
            </span>
          ) : null}
        </span>
      </span>
      {chevron ? (
        <ChevronRight className="mt-2.5 size-4 shrink-0 text-text-muted" aria-hidden />
      ) : null}
    </>
  );
  if (href)
    return (
      <Link href={href} aria-current={active ? 'true' : undefined} className={className}>
        {content}
      </Link>
    );
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={active ? 'true' : undefined}
      className={className}
    >
      {content}
    </button>
  );
}
