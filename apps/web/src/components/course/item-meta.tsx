'use client';
import {
  CheckCircle2,
  ClipboardList,
  File,
  FileText,
  Folder,
  Link2,
  Lock,
  MessagesSquare,
  PlayCircle,
  Timer,
  type LucideIcon,
} from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import type { ItemType, ProgressStatus } from '@/lib/api/schemas/common';
import { cn } from '@/lib/utils/cn';
import { formatDateTime, formatRelative } from '@/lib/utils/format';

export const ITEM_TYPE_ICONS: Record<ItemType, LucideIcon> = {
  page: FileText,
  file: File,
  url: Link2,
  folder: Folder,
  video: PlayCircle,
  assignment: ClipboardList,
  quiz: Timer,
  forum: MessagesSquare,
};

export function ItemTypeIcon({ type, className }: { type: ItemType; className?: string }) {
  const t = useTranslations('itemTypes');
  const Icon = ITEM_TYPE_ICONS[type];
  return (
    <span
      className={cn(
        'flex size-8 shrink-0 items-center justify-center rounded-md bg-primary-soft text-primary',
        className,
      )}
      title={t(type)}
    >
      <Icon className="size-4" aria-hidden />
      <span className="sr-only">{t(type)}</span>
    </span>
  );
}

const STATUS_TONES: Record<ProgressStatus, BadgeTone> = {
  not_started: 'neutral',
  in_progress: 'info',
  draft: 'warning',
  submitted: 'primary',
  submitted_late: 'warning',
  graded: 'success',
  returned: 'danger',
};

export function StatusBadge({ status }: { status: ProgressStatus | null }) {
  const t = useTranslations('status');
  if (!status) return null;
  return <Badge tone={STATUS_TONES[status]}>{t(status)}</Badge>;
}

export function CompletionBadge() {
  const t = useTranslations('status');
  return (
    <Badge tone="success">
      <CheckCircle2 aria-hidden /> {t('complete')}
    </Badge>
  );
}

/** UX-07: locked items explain why — tooltip for pointer users and visible inline text for everyone. */
export function LockedReason({ reasons }: { reasons: string[] }) {
  const t = useTranslations('status');
  const text = reasons.length > 0 ? reasons.join('; ') : t('lockedNoReason');
  return (
    <span className="flex min-w-0 items-start gap-1.5 text-xs text-text-muted">
      <Tooltip content={text}>
        <span
          tabIndex={0}
          className="mt-0.5 shrink-0 rounded-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
        >
          <Lock className="size-3.5" aria-label={t('locked')} />
        </span>
      </Tooltip>
      <span>
        {t('opensWhen')} {text}
      </span>
    </span>
  );
}

export function DueLabel({ dueAt, className }: { dueAt: string | null; className?: string }) {
  const t = useTranslations('status');
  const locale = useLocale();
  if (!dueAt) return null;
  const overdue = new Date(dueAt).getTime() < Date.now();
  return (
    <time
      dateTime={dueAt}
      title={formatDateTime(dueAt, locale)}
      className={cn('text-xs', overdue ? 'font-medium text-danger' : 'text-text-muted', className)}
    >
      {t('due', { when: formatRelative(dueAt, locale) })}
    </time>
  );
}
