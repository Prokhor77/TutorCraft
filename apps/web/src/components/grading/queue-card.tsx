'use client';
import { AlertTriangle, ChevronRight, Hourglass } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import type { QueueEntry } from '@/lib/api/schemas/assessment';
import { cn } from '@/lib/utils/cn';
import { formatRelative } from '@/lib/utils/format';

type CardEntry = Pick<QueueEntry, 'id' | 'kind' | 'userName' | 'itemTitle'> &
  Partial<Pick<QueueEntry, 'courseTitle' | 'submittedAt' | 'late'>>;

/**
 * Stitch queue card: avatar, student, work, «45 мин назад» chip and a status line (waiting / late);
 * the current entry gets the violet active bar. Renders a link (`href`) or a button (`onSelect`).
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
    'relative flex w-full items-start gap-3 rounded-md border bg-surface p-3.5 text-left shadow-sm transition-[box-shadow,border-color] duration-fast hover:border-card-border-hover hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
    active ? 'border-accent/40 bg-primary-soft/40' : 'border-card-border',
  );
  const content = (
    <>
      {active ? (
        <span aria-hidden className="absolute inset-y-3 left-0 w-1 rounded-full bg-primary" />
      ) : null}
      <Avatar name={name} size="md" className="shrink-0" />
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="flex items-start justify-between gap-2">
          <span className="truncate font-semibold">{name}</span>
          {entry.submittedAt ? (
            <span className="shrink-0 rounded-full bg-warning-soft px-2 py-0.5 text-label-sm text-warning">
              {formatRelative(entry.submittedAt, locale)}
            </span>
          ) : null}
        </span>
        <span className="truncate text-xs text-text-muted">
          {entry.itemTitle}
          {showCourse && entry.courseTitle ? ` · ${entry.courseTitle}` : ''}
        </span>
        <span className="flex flex-wrap items-center gap-2 text-xs">
          {entry.late ? (
            <span className="flex items-center gap-1 font-semibold text-danger">
              <AlertTriangle className="size-3.5" aria-hidden /> {t('late')}
            </span>
          ) : (
            <span className="flex items-center gap-1 font-semibold text-primary">
              <Hourglass className="size-3.5" aria-hidden /> {t('waiting')}
            </span>
          )}
          <Badge tone={entry.kind === 'essay' ? 'info' : 'neutral'}>
            {t(`kinds.${entry.kind}`)}
          </Badge>
        </span>
      </span>
      {chevron ? (
        <ChevronRight className="mt-2 size-4 shrink-0 text-text-muted" aria-hidden />
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
