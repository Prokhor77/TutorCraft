'use client';
import { BookOpen, Clock, Layers, Pencil, Trash2, Users } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useState, type ReactNode } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { toast } from '@/components/ui/toast';
import { useDeleteEvent } from '@/features/calendar/use-calendar';
import { ROUTES } from '@/features/auth/routes';
import type { CalendarEvent } from '@/lib/api/schemas/me';
import { formatDate, formatTime } from '@/lib/utils/format';
import { KIND_TONE } from './event-style';

function DetailRow({ icon: Icon, children }: { icon: typeof Clock; children: ReactNode }) {
  return (
    <li className="flex items-start gap-2.5 text-sm">
      <Icon className="mt-0.5 size-4 shrink-0 text-text-muted" aria-hidden />
      <span className="min-w-0">{children}</span>
    </li>
  );
}

function useWhen(event: CalendarEvent): string {
  const t = useTranslations('calendar');
  const locale = useLocale();
  const day = formatDate(event.startsAt, locale);
  if (event.allDay) return `${day} · ${t('allDay')}`;
  const start = formatTime(event.startsAt, locale);
  return event.endsAt
    ? `${day} · ${start}–${formatTime(event.endsAt, locale)}`
    : `${day} · ${start}`;
}

/** Lesson context: course, module, linked course item and (for the tutor) who it is for. */
function LessonRows({ event }: { event: CalendarEvent }) {
  const t = useTranslations('calendar');
  return (
    <>
      {event.courseId && event.courseTitle ? (
        <DetailRow icon={BookOpen}>
          <Link
            href={ROUTES.course(event.courseId)}
            className="font-medium text-primary hover:underline"
          >
            {event.courseTitle}
          </Link>
        </DetailRow>
      ) : null}
      {event.moduleTitle || event.itemTitle ? (
        <DetailRow icon={Layers}>
          {event.moduleTitle ? <span className="block">{event.moduleTitle}</span> : null}
          {event.courseId && event.itemId && event.itemTitle ? (
            <Link
              href={ROUTES.item(event.courseId, event.itemId)}
              className="font-medium text-primary hover:underline"
            >
              {event.itemTitle}
            </Link>
          ) : null}
        </DetailRow>
      ) : null}
      {event.canEdit ? (
        <DetailRow icon={Users}>
          {event.audience === 'students'
            ? t('audienceCount', { count: event.attendeeIds.length })
            : t('audienceCourse')}
        </DetailRow>
      ) : null}
    </>
  );
}

/** Note or lesson details; the author (or course tutor) can edit or delete it. */
export function EventDetailsDialog({
  event,
  onClose,
  onEdit,
}: {
  event: CalendarEvent | null;
  onClose: () => void;
  onEdit: (event: CalendarEvent) => void;
}) {
  const tCommon = useTranslations('common');
  return (
    <Dialog open={event !== null} onOpenChange={(open) => !open && onClose()}>
      {event ? (
        <DialogContent title={event.title} closeLabel={tCommon('close')}>
          <DetailsBody event={event} onClose={onClose} onEdit={onEdit} />
        </DialogContent>
      ) : null}
    </Dialog>
  );
}

function DetailsBody({
  event,
  onClose,
  onEdit,
}: {
  event: CalendarEvent;
  onClose: () => void;
  onEdit: (event: CalendarEvent) => void;
}) {
  const t = useTranslations('calendar');
  const [confirming, setConfirming] = useState(false);
  const remove = useDeleteEvent();
  const when = useWhen(event);

  const onDelete = () =>
    remove.mutate(
      { id: event.id, courseId: event.kind === 'lesson' ? event.courseId : null },
      {
        onSuccess: () => {
          toast({ tone: 'success', title: t('deleted') });
          onClose();
        },
      },
    );

  return (
    <>
      <Badge tone={KIND_TONE[event.kind]} className="self-start">
        {t(`kinds.${event.kind}`)}
      </Badge>
      <ul className="flex flex-col gap-2.5">
        <DetailRow icon={Clock}>{when}</DetailRow>
        {event.kind === 'lesson' ? <LessonRows event={event} /> : null}
      </ul>
      {event.description ? (
        <p className="whitespace-pre-wrap rounded-md bg-surface-muted/60 px-4 py-3 text-sm">
          {event.description}
        </p>
      ) : null}
      {event.canEdit ? (
        <DialogFooter>
          <Button
            variant={confirming ? 'danger' : 'ghost'}
            loading={remove.isPending}
            onClick={() => (confirming ? onDelete() : setConfirming(true))}
          >
            <Trash2 aria-hidden /> {confirming ? t('confirmDelete') : t('delete')}
          </Button>
          <Button variant="secondary" onClick={() => onEdit(event)}>
            <Pencil aria-hidden /> {t('edit')}
          </Button>
        </DialogFooter>
      ) : null}
      {confirming && event.kind === 'lesson' ? (
        <p className="text-xs text-text-muted">{t('deleteLessonHint')}</p>
      ) : null}
    </>
  );
}
