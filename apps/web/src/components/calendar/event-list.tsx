'use client';
import { useLocale, useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import type { CalendarEvent } from '@/lib/api/schemas/me';
import { formatDate } from '@/lib/utils/format';
import { EventChip } from './event-chip';
import { KIND_TONE } from './event-style';

/** Stitch list rows: date block, event chip, kind chip. */
export function EventList({
  events,
  onSelect,
}: {
  events: CalendarEvent[];
  onSelect: (event: CalendarEvent) => void;
}) {
  const t = useTranslations('calendar');
  const locale = useLocale();
  return (
    <ul className="flex flex-col gap-2">
      {[...events]
        .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
        .map((event) => {
          const date = new Date(event.startsAt);
          return (
            <li
              key={event.id}
              className="flex items-center gap-3 rounded-md bg-surface-muted/50 p-2 pr-3 sm:pr-4"
            >
              <time
                dateTime={event.startsAt}
                title={formatDate(event.startsAt, locale)}
                className="flex w-14 shrink-0 flex-col items-center rounded bg-surface py-1.5 shadow-sm"
              >
                <span className="font-heading text-lg font-semibold leading-none">
                  {date.getDate()}
                </span>
                <span className="text-label-sm uppercase text-text-muted">
                  {new Intl.DateTimeFormat(locale, { month: 'short' }).format(date)}
                </span>
              </time>
              <span className="min-w-0 flex-1">
                <EventChip event={event} onSelect={onSelect} />
                {event.courseTitle && event.kind === 'lesson' ? (
                  <span className="block truncate px-1.5 text-label-sm text-text-muted">
                    {event.courseTitle}
                  </span>
                ) : null}
              </span>
              <Badge tone={KIND_TONE[event.kind]} className="hidden sm:inline-flex">
                {t(`kinds.${event.kind}`)}
              </Badge>
            </li>
          );
        })}
    </ul>
  );
}
