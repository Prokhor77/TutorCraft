'use client';
import { useMutation, useQuery } from '@tanstack/react-query';
import { CalendarDays, ChevronLeft, ChevronRight, Copy, Rss } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Input } from '@/components/ui/input';
import { PageHeader, Panel } from '@/components/ui/page-header';
import { Segmented } from '@/components/ui/segmented';
import { SkeletonList } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toast';
import { ROUTES } from '@/features/auth/routes';
import { queryKeys } from '@/features/query-keys';
import { meApi } from '@/lib/api/endpoints/me';
import { CALENDAR_EVENT_KINDS, type CalendarEvent } from '@/lib/api/schemas/me';
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
import { copyToClipboard } from '@/lib/utils/download';
import { formatDate, formatTime } from '@/lib/utils/format';

const KIND_TONE: Record<CalendarEvent['kind'], BadgeTone> = {
  due: 'danger',
  open: 'success',
  close: 'warning',
  personal: 'info',
};
const KIND_DOT: Record<CalendarEvent['kind'], string> = {
  due: 'bg-danger',
  open: 'bg-success',
  close: 'bg-warning',
  personal: 'bg-info',
};
const MAX_EVENTS_IN_CELL = 3;
const MAX_DOTS_IN_CELL = 4;

function EventChip({ event, pill }: { event: CalendarEvent; pill?: boolean }) {
  const locale = useLocale();
  const content = (
    <span className="flex min-w-0 items-center gap-1.5 text-xs">
      <span className={cn('size-1.5 shrink-0 rounded-full', KIND_DOT[event.kind])} aria-hidden />
      <span className="shrink-0 font-medium text-text-muted">
        {formatTime(event.startsAt, locale)}
      </span>
      <span className="truncate">{event.title}</span>
    </span>
  );
  const shape = pill
    ? 'block rounded-full bg-surface px-2 py-0.5 shadow-sm'
    : 'block rounded-full px-1.5 py-0.5';
  if (event.courseId && event.itemId) {
    return (
      <Link
        href={ROUTES.item(event.courseId, event.itemId)}
        className={cn(
          shape,
          'transition-colors duration-fast hover:bg-primary-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
        )}
      >
        {content}
      </Link>
    );
  }
  return <span className={shape}>{content}</span>;
}

function KindLegend() {
  const t = useTranslations('calendar');
  return (
    <ul className="flex flex-wrap items-center gap-x-4 gap-y-1.5" aria-label={t('legend')}>
      {CALENDAR_EVENT_KINDS.map((kind) => (
        <li key={kind} className="flex items-center gap-1.5 text-label-md text-text-muted">
          <span className={cn('size-2 rounded-full', KIND_DOT[kind])} aria-hidden />
          {t(`kinds.${kind}`)}
        </li>
      ))}
    </ul>
  );
}

/** Stitch list rows: date block, event chip, kind chip. */
function EventList({ events }: { events: CalendarEvent[] }) {
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
                <EventChip event={event} />
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

function IcalDialog() {
  const t = useTranslations('calendar');
  const tCommon = useTranslations('common');
  const issue = useMutation({ mutationFn: meApi.icalToken });
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <Rss aria-hidden /> {t('subscribe')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('icalTitle')}
        description={t('icalHint')}
        closeLabel={tCommon('close')}
      >
        {issue.data ? (
          <div className="flex gap-2">
            <Input
              readOnly
              value={issue.data.url}
              aria-label={t('icalUrl')}
              onFocus={(event) => event.target.select()}
            />
            <Button
              onClick={() =>
                issue.data &&
                void copyToClipboard(issue.data.url).then((ok) =>
                  toast({
                    tone: ok ? 'success' : 'error',
                    title: ok ? t('copied') : t('copyFailed'),
                  }),
                )
              }
            >
              <Copy aria-hidden /> {t('copy')}
            </Button>
          </div>
        ) : null}
        <Button
          variant={issue.data ? 'ghost' : 'primary'}
          loading={issue.isPending}
          onClick={() => issue.mutate()}
        >
          {issue.data ? t('reissue') : t('getLink')}
        </Button>
        {issue.data ? <p className="text-xs text-text-muted">{t('reissueHint')}</p> : null}
      </DialogContent>
    </Dialog>
  );
}

/** FR-DASH-03: month / week / list, iCal subscription by secret link. */
export default function CalendarPage() {
  const t = useTranslations('calendar');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const [view, setView] = useState<CalendarView>('month');
  const [anchor, setAnchor] = useState(() => new Date());
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
        actions={<IcalDialog />}
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
                        <span
                          className={cn(
                            'flex size-6 items-center justify-center self-center rounded-full text-xs font-semibold sm:size-7 sm:self-start',
                            isToday && 'bg-primary text-primary-foreground shadow-sm',
                          )}
                        >
                          {day.getDate()}
                        </span>
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
                            <EventChip key={event.id} event={event} pill />
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
              <EventList events={monthEvents} />
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
                    <span
                      className={cn(
                        'flex size-8 items-center justify-center rounded-full font-heading text-lg font-semibold',
                        isToday && 'bg-primary text-primary-foreground shadow-sm',
                      )}
                    >
                      {day.getDate()}
                    </span>
                  </span>
                  {dayEvents.map((event) => (
                    <EventChip key={event.id} event={event} pill />
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
            <EventList events={events.data} />
          </Panel>
        )
      ) : null}
    </>
  );
}
