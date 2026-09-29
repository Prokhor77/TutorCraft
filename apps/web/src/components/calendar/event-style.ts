import type { BadgeTone } from '@/components/ui/badge';
import type { CalendarEvent } from '@/lib/api/schemas/me';

type Kind = CalendarEvent['kind'];

export const KIND_TONE: Record<Kind, BadgeTone> = {
  due: 'danger',
  open: 'success',
  close: 'warning',
  lesson: 'primary',
  personal: 'info',
};

export const KIND_DOT: Record<Kind, string> = {
  due: 'bg-danger',
  open: 'bg-success',
  close: 'bg-warning',
  lesson: 'bg-primary',
  personal: 'bg-info',
};

/** Notes and lessons open a details dialog; item dates link straight to the item. */
export function isSelectable(event: CalendarEvent): boolean {
  return event.kind === 'lesson' || event.kind === 'personal';
}
