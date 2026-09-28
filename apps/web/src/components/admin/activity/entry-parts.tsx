'use client';
import { AlertTriangle, Eye, Globe, MousePointerClick } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import { entryTone, isSlow } from '@/features/activity/describe-entry';
import type { ActivityEntry } from '@/lib/api/schemas/activity';
import { cn } from '@/lib/utils/cn';

const KIND_ICONS = {
  request: MousePointerClick,
  page_view: Eye,
  client_error: AlertTriangle,
} as const;

export function KindIcon({ entry, className }: { entry: ActivityEntry; className?: string }) {
  const Icon = KIND_ICONS[entry.kind] ?? Globe;
  return <Icon className={cn('size-4 shrink-0', className)} aria-hidden />;
}

/** Status chip («200», «404 course.not_found», «Ошибка в браузере») plus duration. */
export function OutcomeBadge({ entry }: { entry: ActivityEntry }) {
  const t = useTranslations('adminActivity');
  const label =
    entry.kind === 'page_view'
      ? t('kind.page_view')
      : entry.kind === 'client_error'
        ? t('kind.client_error')
        : String(entry.status ?? '—');
  return (
    <span className="flex flex-col items-start gap-1">
      <Badge tone={entryTone(entry)}>{label}</Badge>
      {entry.errorCode && entry.kind === 'request' ? (
        <span className="max-w-48 truncate font-mono text-xs text-text-muted">
          {entry.errorCode}
        </span>
      ) : null}
      {entry.durationMs !== null ? (
        <span
          className={cn(
            'text-xs',
            isSlow(entry) ? 'font-semibold text-warning' : 'text-text-muted',
          )}
        >
          {t('duration', { ms: entry.durationMs })}
        </span>
      ) : null}
    </span>
  );
}
