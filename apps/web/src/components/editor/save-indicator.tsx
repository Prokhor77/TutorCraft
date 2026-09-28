'use client';
import { AlertCircle, Check, CloudOff, Loader2, PenLine } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import type { AutosaveStatus } from '@/features/editor/use-autosave';
import { cn } from '@/lib/utils/cn';
import { formatTime } from '@/lib/utils/format';

const icons = {
  idle: Check,
  dirty: PenLine,
  saving: Loader2,
  saved: Check,
  error: CloudOff,
  invalid: AlertCircle,
} as const;

/** "Saved" indicator for autosave (UX-03) and quiz answers (FR-QUIZ-03). */
export function SaveIndicator({
  status,
  lastSavedAt,
  className,
}: {
  status: AutosaveStatus;
  lastSavedAt?: Date | null;
  className?: string;
}) {
  const t = useTranslations('autosave');
  const locale = useLocale();
  const Icon = icons[status];
  const time = lastSavedAt ? formatTime(lastSavedAt.toISOString(), locale) : '';
  return (
    <span
      role="status"
      aria-live="polite"
      className={cn(
        'inline-flex items-center gap-1.5 text-xs text-text-muted',
        (status === 'error' || status === 'invalid') && 'text-danger',
        className,
      )}
    >
      <Icon className={cn('size-3.5', status === 'saving' && 'animate-spin')} aria-hidden />
      {status === 'saved' && time ? t('savedAt', { time }) : t(status)}
    </span>
  );
}
