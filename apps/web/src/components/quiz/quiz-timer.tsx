'use client';
import { Timer } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { cn } from '@/lib/utils/cn';
import { formatDuration, timerLevel } from '@/lib/utils/timer';

const levelClass = {
  normal: 'bg-surface-muted text-text',
  warning: 'bg-warning-soft text-warning',
  critical: 'bg-danger-soft text-danger',
  expired: 'bg-danger text-danger-foreground',
} as const;

export function QuizTimer({ remaining }: { remaining: number | null }) {
  const t = useTranslations('quiz');
  if (remaining === null) return null;
  const level = timerLevel(remaining);
  return (
    <span
      role="timer"
      aria-live={level === 'critical' ? 'assertive' : 'off'}
      aria-label={t('timeLeft', { time: formatDuration(remaining) })}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-3 py-1 font-mono text-sm font-semibold tabular-nums',
        levelClass[level],
      )}
    >
      <Timer className="size-4" aria-hidden />
      {formatDuration(remaining)}
    </span>
  );
}
