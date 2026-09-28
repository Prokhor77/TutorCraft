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
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { Segmented } from '@/components/ui/segmented';
import { SkeletonList } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toast';
import { ROUTES } from '@/features/auth/routes';
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
import { copyToClipboard } from '@/lib/utils/download';
import { formatDate, formatTime } from '@/lib/utils/format';

const KIND_TONE: Record<CalendarEvent['kind'], BadgeTone> = {
  due: 'danger',
  open: 'success',
  close: 'warning',
  personal: 'info',
};
const MAX_EVENTS_IN_CELL = 3;

function EventChip({ event }: { event: CalendarEvent }) {
  const locale = useLocale();
  const content = (
    <span className="flex min-w-0 items-center gap-1 text-xs">
      <span
        className={cn(
          'size-1.5 shrink-0 rounded-full',
          { due: 'bg-danger', open: 'bg-success', close: 'bg-warning', personal: 'bg-info' }[
            event.kind
          ],
        )}
        aria-hidden
      />
      <span className="shrink-0 text-text-muted">{formatTime(event.startsAt, locale)}</span>
      <span className="truncate">{event.title}</span>
    </span>
  );
  if (event.courseId && event.itemId) {
    return (
      <Link
        href={ROUTES.item(event.courseId, event.itemId)}
        className="block rounded-xs px-1 hover:bg-primary-soft"
      >
        {content}
      </Link>
    );
  }
  return <span className="block px-1">{content}</span>;
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
  const title =
    view === 'month'
      ? new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(anchor)
      : `${formatDate(from.toISOString(), locale)} — ${formatDate(new Date(to.getTime() - 1).toISOString(), locale)}`;
  const weekdayNames = weekDays(today).map((day) =>
    new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(day),
  );

  return (
    <>
      <PageHeader title={t('title')} actions={<IcalDialog />} />
      <div className="mb-4 flex flex-wrap items-center gap-2">
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
        <h2 className="text-lg capitalize" aria-live="polite">
          {title}
        </h2>
        <Segmented<CalendarView>
          className="ml-auto"
          label={t('view')}
          value={view}
          onChange={setView}
          options={[
            { value: 'month', label: t('month') },
            { value: 'week', label: t('week') },
            { value: 'list', label: t('list') },
          ]}
        />
      </div>
      {events.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {view === 'month' && events.isSuccess ? (
        <div className="overflow-x-auto">
          <div
            role="grid"
            aria-label={title}
            className="grid min-w-[42rem] grid-cols-7 overflow-hidden rounded-lg border border-border bg-border [gap:1px]"
          >
            {weekdayNames.map((name) => (
              <div
                key={name}
                role="columnheader"
                className="bg-surface-muted px-2 py-1.5 text-xs font-semibold uppercase text-text-muted"
              >
                {name}
              </div>
            ))}
            {monthGrid(anchor)
              .flat()
              .map((day) => {
                const dayEvents = byDay.get(dayKey(day)) ?? [];
                const outside = day.getMonth() !== anchor.getMonth();
                return (
                  <div
                    key={day.toISOString()}
                    role="gridcell"
                    aria-label={formatDate(day.toISOString(), locale)}
                    className={cn(
                      'flex min-h-24 flex-col gap-0.5 bg-surface p-1',
                      outside && 'bg-surface-muted/60 text-text-muted',
                    )}
                  >
                    <span
                      className={cn(
                        'self-end rounded-full px-1.5 text-xs',
                        sameLocalDay(day, today) &&
                          'bg-primary font-semibold text-primary-foreground',
                      )}
                    >
                      {day.getDate()}
                    </span>
                    {dayEvents.slice(0, MAX_EVENTS_IN_CELL).map((event) => (
                      <EventChip key={event.id} event={event} />
                    ))}
                    {dayEvents.length > MAX_EVENTS_IN_CELL ? (
                      <span className="px-1 text-xs text-text-muted">
                        {t('more', { count: dayEvents.length - MAX_EVENTS_IN_CELL })}
                      </span>
                    ) : null}
                  </div>
                );
              })}
          </div>
        </div>
      ) : null}
      {view === 'week' && events.isSuccess ? (
        <ol className="grid grid-cols-1 gap-2 md:grid-cols-7">
          {weekDays(anchor).map((day) => (
            <li
              key={day.toISOString()}
              className={cn(
                'flex min-h-32 flex-col gap-1 rounded-lg border border-border bg-surface p-2',
                sameLocalDay(day, today) && 'border-primary',
              )}
            >
              <span className="text-xs font-semibold capitalize">
                {new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric' }).format(day)}
              </span>
              {(byDay.get(dayKey(day)) ?? []).map((event) => (
                <EventChip key={event.id} event={event} />
              ))}
            </li>
          ))}
        </ol>
      ) : null}
      {view === 'list' && events.isSuccess ? (
        events.data.length === 0 ? (
          <EmptyState icon={CalendarDays} title={t('emptyTitle')} description={t('emptyText')} />
        ) : (
          <ul className="flex flex-col gap-2">
            {[...events.data]
              .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
              .map((event) => (
                <li
                  key={event.id}
                  className="flex items-center gap-3 rounded-md border border-border bg-surface px-3 py-2"
                >
                  <span className="w-28 shrink-0 text-sm text-text-muted">
                    {formatDate(event.startsAt, locale)}
                  </span>
                  <span className="min-w-0 flex-1">
                    <EventChip event={event} />
                  </span>
                  <Badge tone={KIND_TONE[event.kind]}>{t(`kinds.${event.kind}`)}</Badge>
                </li>
              ))}
          </ul>
        )
      ) : null}
    </>
  );
}
