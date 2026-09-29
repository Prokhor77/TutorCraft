'use client';
import { useTranslations } from 'next-intl';
import { CALENDAR_EVENT_KINDS } from '@/lib/api/schemas/me';
import { cn } from '@/lib/utils/cn';
import { KIND_DOT } from './event-style';

export function KindLegend() {
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
