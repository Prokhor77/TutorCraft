'use client';
import { Clock } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { cn } from '@/lib/utils/cn';
import { formatDateTime } from '@/lib/utils/format';
import { MS_PER_DAY, MS_PER_HOUR, MS_PER_MINUTE } from '@/lib/utils/time';

const TICK_MS = 30_000;

/** Deadline countdown for the submission screen (SPEC §10 «Сдача задания»). */
export function DeadlineCountdown({ dueAt }: { dueAt: string }) {
  const t = useTranslations('assignment');
  const locale = useLocale();
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(timer);
  }, []);
  const remaining = new Date(dueAt).getTime() - now;
  const overdue = remaining < 0;
  const abs = Math.abs(remaining);
  const days = Math.floor(abs / MS_PER_DAY);
  const hours = Math.floor((abs % MS_PER_DAY) / MS_PER_HOUR);
  const minutes = Math.floor((abs % MS_PER_HOUR) / MS_PER_MINUTE);
  const urgent = !overdue && remaining < MS_PER_DAY;
  return (
    <div
      className={cn(
        'flex items-center gap-2 rounded px-3 py-2 text-sm',
        overdue
          ? 'bg-danger-soft text-danger'
          : urgent
            ? 'bg-warning-soft text-warning'
            : 'bg-surface-muted text-text',
      )}
    >
      <Clock className="size-4" aria-hidden />
      <span>
        <span className="font-semibold">
          {t(overdue ? 'overdueBy' : 'timeLeft', { days, hours, minutes })}
        </span>
        <span className="text-text-muted">
          {' '}
          · {t('dueOn', { date: formatDateTime(dueAt, locale) })}
        </span>
      </span>
    </div>
  );
}
