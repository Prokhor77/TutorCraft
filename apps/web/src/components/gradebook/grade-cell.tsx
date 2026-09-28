'use client';
import { History, Lock, Unlock } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { useGradeHistory, type CellTarget } from '@/features/gradebook/use-gradebook';
import { cn } from '@/lib/utils/cn';
import { formatDateTime } from '@/lib/utils/format';

function HistoryPopover({
  gradeId,
  locked,
  onToggleLock,
}: {
  gradeId: string;
  locked: boolean;
  onToggleLock: () => void;
}) {
  const t = useTranslations('gradebook');
  const locale = useLocale();
  const [open, setOpen] = useState(false);
  const history = useGradeHistory(open ? gradeId : null);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="rounded-xs p-0.5 text-text-muted opacity-0 hover:text-text focus-visible:opacity-100 group-hover:opacity-100"
          aria-label={t('history')}
        >
          <History className="size-3.5" aria-hidden />
        </button>
      </PopoverTrigger>
      <PopoverContent className="flex w-80 flex-col gap-3">
        <h3 className="text-sm font-semibold">{t('history')}</h3>
        {history.data?.length === 0 ? (
          <p className="text-xs text-text-muted">{t('noHistory')}</p>
        ) : null}
        <ul className="flex max-h-52 flex-col gap-1.5 overflow-y-auto text-xs">
          {history.data?.map((entry, index) => (
            <li key={index}>
              <span className="font-medium">{entry.actorName}</span> ·{' '}
              {formatDateTime(entry.at, locale)}: {entry.oldScore ?? '—'} → {entry.newScore ?? '—'}
            </li>
          ))}
        </ul>
        <Button size="sm" variant="secondary" onClick={onToggleLock}>
          {locked ? <Unlock aria-hidden /> : <Lock aria-hidden />}{' '}
          {locked ? t('unlock') : t('lock')}
        </Button>
      </PopoverContent>
    </Popover>
  );
}

type Props = {
  target: CellTarget;
  maxScore: number;
  label: string;
  editable: boolean;
  onSave: (score: number | null, locked?: boolean) => void;
};

/** Inline-editable grade cell: Enter/blur saves, Escape cancels (FR-GRADE-04). */
export function GradeCellView({ target, maxScore, label, editable, onSave }: Props) {
  const t = useTranslations('gradebook');
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const cell = target.cell;
  const commit = () => {
    setEditing(false);
    const next = draft.trim() === '' ? null : Number(draft.replace(',', '.'));
    if (next !== null && (!Number.isFinite(next) || next < 0 || next > maxScore)) return;
    if (next === (cell?.score ?? null)) return;
    onSave(next);
  };
  if (editing) {
    return (
      <input
        autoFocus
        aria-label={label}
        inputMode="decimal"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === 'Enter') commit();
          if (event.key === 'Escape') setEditing(false);
        }}
        className="h-8 w-16 rounded-xs border border-primary bg-surface px-1.5 text-right text-sm focus:outline-none focus:ring-2 focus:ring-focus-ring/40"
      />
    );
  }
  return (
    <span className="group inline-flex items-center gap-1">
      <button
        type="button"
        disabled={!editable || cell?.locked}
        aria-label={`${label}: ${cell?.score ?? t('noGrade')}`}
        onClick={() => {
          setDraft(cell?.score?.toString() ?? '');
          setEditing(true);
        }}
        className={cn(
          'min-w-10 rounded-xs px-1.5 py-1 text-right tabular-nums hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring disabled:cursor-default disabled:hover:bg-transparent',
          cell && !cell.published && cell.score !== null && 'italic text-text-muted',
        )}
      >
        {cell?.score ?? '—'}
      </button>
      {cell?.overridden ? (
        <span
          className="size-1.5 rounded-full bg-warning"
          title={t('overridden')}
          aria-label={t('overridden')}
        />
      ) : null}
      {cell?.locked ? <Lock className="size-3 text-text-muted" aria-label={t('locked')} /> : null}
      {cell?.gradeId && editable ? (
        <HistoryPopover
          gradeId={cell.gradeId}
          locked={cell.locked}
          onToggleLock={() => onSave(cell.score, !cell.locked)}
        />
      ) : null}
    </span>
  );
}
