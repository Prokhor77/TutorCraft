'use client';
import { ListTree, UserX } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { PersonCell } from '@/components/admin/admin-panel';
import type { BadgeTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { entryObjects, entryTarget, entryTone } from '@/features/activity/describe-entry';
import type { ActivityEntry } from '@/lib/api/schemas/activity';
import { cn } from '@/lib/utils/cn';
import { formatDateTimePrecise } from '@/lib/utils/format';
import { KindIcon, OutcomeBadge } from './entry-parts';

/** Failures stand out in the list: server/browser errors red, rejected requests amber. */
const ROW_TINT: Partial<Record<BadgeTone, string>> = {
  danger: 'bg-danger-soft/40',
  warning: 'bg-warning-soft/30',
};

/** Who did what, where and how it ended; every row opens the trail (what happened around it). */
export function ActivityTable({
  entries,
  onOpen,
  showSchool = false,
}: {
  entries: ActivityEntry[];
  onOpen: (entryId: string) => void;
  /** Adds the school column (the log shows every school). */
  showSchool?: boolean;
}) {
  const t = useTranslations('adminActivity');
  const locale = useLocale();
  return (
    <Table>
      <THead>
        <tr>
          <TH className="hidden md:table-cell">{t('columns.at')}</TH>
          <TH>{t('columns.actor')}</TH>
          {showSchool ? <TH className="hidden md:table-cell">{t('columns.school')}</TH> : null}
          <TH>{t('columns.action')}</TH>
          <TH className="hidden lg:table-cell">{t('columns.page')}</TH>
          <TH>{t('columns.outcome')}</TH>
          <TH>
            <span className="sr-only">{t('columns.trail')}</span>
          </TH>
        </tr>
      </THead>
      <TBody>
        {entries.map((entry) => (
          <TR key={entry.id} className={cn(ROW_TINT[entryTone(entry)])}>
            <TD className="hidden whitespace-nowrap text-xs text-text-muted md:table-cell">
              {formatDateTimePrecise(entry.at, locale)}
            </TD>
            <TD className="min-w-48">
              <ActorCell entry={entry} at={formatDateTimePrecise(entry.at, locale)} />
            </TD>
            {showSchool ? (
              <TD className="hidden max-w-48 truncate text-sm md:table-cell">
                {entry.tenantName ?? (
                  <span className="text-text-muted">
                    {entry.tenantId ? t('deletedSchool') : t('noSchool')}
                  </span>
                )}
              </TD>
            ) : null}
            <TD className="min-w-64">
              <ActionCell entry={entry} />
            </TD>
            <TD className="hidden max-w-56 truncate font-mono text-xs text-text-muted lg:table-cell">
              {entry.page ?? '—'}
            </TD>
            <TD>
              <OutcomeBadge entry={entry} />
            </TD>
            <TD>
              <Button variant="secondary" size="sm" onClick={() => onOpen(entry.id)}>
                <ListTree aria-hidden /> {t('trail.open')}
              </Button>
            </TD>
          </TR>
        ))}
      </TBody>
    </Table>
  );
}

function ActorCell({ entry, at }: { entry: ActivityEntry; at: string }) {
  const t = useTranslations('adminActivity');
  const time = <span className="md:hidden">{at}</span>;
  if (entry.actorName) {
    return (
      <PersonCell
        name={entry.actorName}
        secondary={entry.actorEmail ?? entry.ip}
        extra={<span className="text-xs text-text-muted">{time}</span>}
      />
    );
  }
  return (
    <span className="flex items-center gap-3">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-container text-text-muted">
        <UserX className="size-4" aria-hidden />
      </span>
      <span className="flex min-w-0 flex-col">
        <span className="font-semibold">{t('anonymous')}</span>
        <span className="font-mono text-xs text-text-muted">{entry.ip ?? '—'}</span>
        <span className="text-xs text-text-muted">{time}</span>
      </span>
    </span>
  );
}

function ActionCell({ entry }: { entry: ActivityEntry }) {
  const objects = entryObjects(entry);
  return (
    <span className="flex min-w-0 items-start gap-2">
      <KindIcon entry={entry} className="mt-0.5 text-text-muted" />
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="break-all font-mono text-xs font-semibold">{entryTarget(entry)}</span>
        {entry.errorMessage ? (
          <span className="line-clamp-2 text-xs text-danger">{entry.errorMessage}</span>
        ) : null}
        {objects.length > 0 ? (
          <span className="truncate font-mono text-[0.6875rem] text-text-muted">
            {objects.join(' · ')}
          </span>
        ) : null}
      </span>
    </span>
  );
}
