'use client';
import { useQuery } from '@tanstack/react-query';
import { CalendarDays, ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';
import { EventChip } from '@/components/calendar/event-chip';
import { EventDetailsDialog } from '@/components/calendar/event-details-dialog';
import { EventFormDialog, type EventDraftTarget } from '@/components/calendar/event-form-dialog';
import { EventList } from '@/components/calendar/event-list';
import { KIND_DOT } from '@/components/calendar/event-style';
import { IcalDialog } from '@/components/calendar/ical-dialog';
import { KindLegend } from '@/components/calendar/kind-legend';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { PageHeader, Panel } from '@/components/ui/page-header';
import { Segmented } from '@/components/ui/segmented';
import { SkeletonList } from '@/components/ui/skeleton';
import { useLessonCourses } from '@/features/calendar/use-calendar';
import { queryKeys } from '@/features/query-keys';
import { meApi } from '@/lib/api/endpoints/me';
import type { CalendarEvent } from '@/lib/api/schemas/me';
import {
  dayKey,
  monthGrid,
  rangeFor,
  sameLocalDay,
  shiftAnchor,
  weekDays,
  type CalendarView,
} from '@/lib/utils/calendar';
import { cn } from '@/lib/utils/cn';
import { formatDate } from '@/lib/utils/format';

const MAX_EVENTS_IN_CELL = 3;
const MAX_DOTS_IN_CELL = 4;

/** Day number that opens «new event» for that day. */
function DayButton({
  day,
  isToday,
  onAdd,
  className,
}: {
  day: Date;
  isToday: boolean;
  onAdd: (day: Date) => void;
  className?: string;
}) {
  const t = useTranslations('calendar');
  const locale = useLocale();
  return (
    <button
      type="button"
      onClick={() => onAdd(day)}
      aria-label={t('addOnDay', { date: formatDate(day.toISOString(), locale) })}
      className={cn(
        'flex items-center justify-center rounded-full font-semibold transition-colors duration-fast hover:bg-primary-soft hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
        isToday &&
          'bg-primary text-primary-foreground shadow-sm hover:bg-primary/90 hover:text-primary-foreground',
        className,
      )}
    >
      {day.getDate()}
    </button>
  );
}

/**
 * FR-DASH-03: month / week / list, iCal subscription by secret link. Anyone can add personal notes; tutors schedule
 * lessons for the whole course or chosen students, and students see lessons addressed to them.
 */
export default function CalendarPage() {
  const t = useTranslations('calendar');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const [view, setView] = useState<CalendarView>('month');
  const [anchor, setAnchor] = useState(() => new Date());
  const [draft, setDraft] = useState<EventDraftTarget | null>(null);
  const [selected, setSelected] = useState<CalendarEvent | null>(null);
  const lessonCourses = useLessonCourses();
  const courses = lessonCourses.data ?? [];
  const onlyCourseId = courses.length === 1 ? courses[0]?.id : undefined;
  const addOn = (date: Date) => setDraft({ date, courseId: onlyCourseId });
  const editEvent = (event: CalendarEvent) => {
    setSelected(null);
    setDraft({ event });
  };
  const { from, to } = rangeFor(view, anchor);
  const events = useQuery({
    queryKey: queryKeys.calendar(from.toISOString(), to.toISOString()),
    queryFn: () => meApi.calendar(from.toISOString(), to.toISOString()),
  });
  const byDay = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>();
    for (const event of events.data ?? []) {
      const key = dayKey(new Date(event.startsAt));
      map.set(key, [...(map.get(key) ?? []), event]);
    }
    return map;
  }, [events.data]);
  const today = new Date();
  const rawTitle =
    view === 'month'
      ? new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(anchor)
      : `${formatDate(from.toISOString(), locale)} — ${formatDate(new Date(to.getTime() - 1).toISOString(), locale)}`;
  // Capitalise only the first letter: CSS `capitalize` would also turn «г.» into «Г.».
  const title = rawTitle.charAt(0).toLocaleUpperCase(locale) + rawTitle.slice(1);
  const weekdayNames = weekDays(today).map((day) =>
    new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(day),
  );
  const monthEvents = (events.data ?? []).filter(
    (event) => new Date(event.startsAt).getMonth() === anchor.getMonth(),
  );

  return (
    <>
      <PageHeader
        title={t('title')}
        meta={
          events.isSuccess ? (
            <Badge tone="primary" dot>
              {t('eventsCount', {
                count: view === 'month' ? monthEvents.length : events.data.length,
              })}
            </Badge>
          ) : null
        }
        actions={
          <>
            <IcalDialog />
            <Button size="sm" onClick={() => addOn(new Date())}>
              <Plus aria-hidden /> {t('add')}
            </Button>
          </>
        }
      >
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <Button
            variant="secondary"
            size="icon-sm"
            aria-label={t('previous')}
            onClick={() => setAnchor(shiftAnchor(view, anchor, -1))}
          >
            <ChevronLeft aria-hidden />
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setAnchor(new Date())}>
            {t('today')}
          </Button>
          <Button
            variant="secondary"
            size="icon-sm"
            aria-label={t('next')}
            onClick={() => setAnchor(shiftAnchor(view, anchor, 1))}
          >
            <ChevronRight aria-hidden />
          </Button>
          <h2 className="ml-1 text-lg" aria-live="polite">
            {title}
          </h2>
        </div>
        <Segmented<CalendarView>
          className="w-full sm:w-auto sm:min-w-80"
          label={t('view')}
          value={view}
          onChange={setView}
          options={[
            { value: 'month', label: t('month') },
            { value: 'week', label: t('week') },
            { value: 'list', label: t('list') },
          ]}
        />
      </PageHeader>
      {events.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {events.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void events.refetch()}
        />
      ) : null}
      {view === 'month' && events.isSuccess ? (
        <div className="flex flex-col gap-4 md:gap-gutter">
          <Panel className="p-3 sm:p-5">
            <div className="px-1">
              <KindLegend />
            </div>
            <div role="grid" aria-label={title} className="grid grid-cols-7 gap-1 sm:gap-2">
              <div role="row" className="contents">
                {weekdayNames.map((name) => (
                  <div
                    key={name}
                    role="columnheader"
                    className="py-1 text-center text-label-sm uppercase text-text-muted sm:text-label-md"
                  >
                    {name}
                  </div>
                ))}
              </div>
              {monthGrid(anchor).map((week) => (
                <div key={week[0]?.toISOString()} role="row" className="contents">
                  {week.map((day) => {
                    const dayEvents = byDay.get(dayKey(day)) ?? [];
                    const outside = day.getMonth() !== anchor.getMonth();
                    const isToday = sameLocalDay(day, today);
                    return (
                      <div
                        key={day.toISOString()}
                        role="gridcell"
                        aria-label={formatDate(day.toISOString(), locale)}
                        className={cn(
                          'flex min-h-14 min-w-0 flex-col gap-1 rounded p-1 sm:min-h-28 sm:rounded-md sm:p-2',
                          outside ? 'bg-transparent text-outline' : 'bg-surface-muted/60',
                          isToday && 'bg-primary-soft/70 ring-2 ring-primary/30',
                        )}
                      >
                        <DayButton
                          day={day}
                          isToday={isToday}
                          onAdd={addOn}
                          className="size-6 self-center text-xs sm:size-7 sm:self-start"
                        />
                        {dayEvents.length > 0 ? (
                          <span
                            className="flex flex-wrap justify-center gap-0.5 sm:hidden"
                            aria-hidden
                          >
                            {dayEvents.slice(0, MAX_DOTS_IN_CELL).map((event) => (
                              <span
                                key={event.id}
                                className={cn('size-1.5 rounded-full', KIND_DOT[event.kind])}
                              />
                            ))}
                          </span>
                        ) : null}
                        <div className="hidden min-w-0 flex-col gap-1 sm:flex">
                          {dayEvents.slice(0, MAX_EVENTS_IN_CELL).map((event) => (
                            <EventChip key={event.id} event={event} pill onSelect={setSelected} />
                          ))}
                          {dayEvents.length > MAX_EVENTS_IN_CELL ? (
                            <span className="px-2 text-label-sm text-text-muted">
                              {t('more', { count: dayEvents.length - MAX_EVENTS_IN_CELL })}
                            </span>
                          ) : null}
                        </div>
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          </Panel>
          <Panel title={t('monthAgenda')} className="sm:hidden">
            {monthEvents.length === 0 ? (
              <p className="rounded-md bg-surface-muted/60 px-4 py-3 text-sm text-text-muted">
                {t('monthEmpty')}
              </p>
            ) : (
              <EventList events={monthEvents} onSelect={setSelected} />
            )}
          </Panel>
        </div>
      ) : null}
      {view === 'week' && events.isSuccess ? (
        <Panel className="p-3 sm:p-5">
          <ol className="grid grid-cols-1 gap-2 md:grid-cols-7">
            {weekDays(anchor).map((day) => {
              const isToday = sameLocalDay(day, today);
              const dayEvents = byDay.get(dayKey(day)) ?? [];
              return (
                <li
                  key={day.toISOString()}
                  className={cn(
                    'flex min-h-20 min-w-0 flex-col gap-2 rounded-md bg-surface-muted/60 p-3 md:min-h-64',
                    isToday && 'bg-primary-soft/70 ring-2 ring-primary/30',
                  )}
                >
                  <span className="flex items-center gap-2 md:flex-col md:items-start md:gap-0.5">
                    <span className="text-label-md uppercase text-text-muted">
                      {new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(day)}
                    </span>
                    <DayButton
                      day={day}
                      isToday={isToday}
                      onAdd={addOn}
                      className="size-8 font-heading text-lg"
                    />
                  </span>
                  {dayEvents.map((event) => (
                    <EventChip key={event.id} event={event} pill onSelect={setSelected} />
                  ))}
                </li>
              );
            })}
          </ol>
        </Panel>
      ) : null}
      {view === 'list' && events.isSuccess ? (
        events.data.length === 0 ? (
          <EmptyState icon={CalendarDays} title={t('emptyTitle')} description={t('emptyText')} />
        ) : (
          <Panel actions={<KindLegend />}>
            <EventList events={events.data} onSelect={setSelected} />
          </Panel>
        )
      ) : null}
      <EventFormDialog target={draft} courses={courses} onClose={() => setDraft(null)} />
      <EventDetailsDialog event={selected} onClose={() => setSelected(null)} onEdit={editEvent} />
    </>
  );
}
