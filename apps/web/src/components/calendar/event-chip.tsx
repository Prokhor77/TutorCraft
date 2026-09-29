'use client';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { ROUTES } from '@/features/auth/routes';
import type { CalendarEvent } from '@/lib/api/schemas/me';
import { cn } from '@/lib/utils/cn';
import { formatTime } from '@/lib/utils/format';
import { isSelectable, KIND_DOT } from './event-style';

const INTERACTIVE =
  'text-left transition-colors duration-fast hover:bg-primary-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring';

/** One event: dot, time (or «all day»), title. Notes/lessons open details, item dates link to the item. */
export function EventChip({
  event,
  pill,
  onSelect,
}: {
  event: CalendarEvent;
  pill?: boolean;
  onSelect: (event: CalendarEvent) => void;
}) {
  const t = useTranslations('calendar');
  const locale = useLocale();
  const content = (
    <span className="flex min-w-0 items-center gap-1.5 text-xs">
      <span className={cn('size-1.5 shrink-0 rounded-full', KIND_DOT[event.kind])} aria-hidden />
      <span className="shrink-0 font-medium text-text-muted">
        {event.allDay ? t('allDayShort') : formatTime(event.startsAt, locale)}
      </span>
      <span className="truncate">{event.title}</span>
    </span>
  );
  const shape = pill
    ? 'block w-full rounded-full bg-surface px-2 py-0.5 shadow-sm'
    : 'block w-full rounded-full px-1.5 py-0.5';
  if (isSelectable(event)) {
    return (
      <button type="button" className={cn(shape, INTERACTIVE)} onClick={() => onSelect(event)}>
        {content}
      </button>
    );
  }
  if (event.courseId && event.itemId) {
    return (
      <Link href={ROUTES.item(event.courseId, event.itemId)} className={cn(shape, INTERACTIVE)}>
        {content}
      </Link>
    );
  }
  return <span className={shape}>{content}</span>;
}
