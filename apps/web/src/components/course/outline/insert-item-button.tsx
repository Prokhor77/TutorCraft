'use client';
import { Plus } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import type { ItemType } from '@/lib/api/schemas/common';
import { cn } from '@/lib/utils/cn';
import { ItemTypePicker } from '../item-type-picker';

/** "+" between items (SPEC §10): opens the searchable type picker. */
export function InsertItemButton({
  onPick,
  prominent,
  label,
}: {
  onPick: (type: ItemType) => void;
  prominent?: boolean;
  label?: string;
}) {
  const t = useTranslations('course');
  const [open, setOpen] = useState(false);
  return (
    <ItemTypePicker open={open} onOpenChange={setOpen} onPick={onPick}>
      <button
        type="button"
        aria-label={prominent ? undefined : (label ?? t('addItemHere'))}
        className={cn(
          'group/insert flex w-full items-center gap-2 rounded-md text-xs font-medium text-text-muted transition-opacity focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
          prominent
            ? 'border border-dashed border-border px-3 py-2.5 hover:border-primary hover:text-primary'
            : 'h-3 opacity-0 hover:h-7 hover:opacity-100 focus-visible:h-7 data-[state=open]:h-7 data-[state=open]:opacity-100',
        )}
      >
        <span
          className="h-px flex-1 bg-border group-hover/insert:bg-primary"
          aria-hidden={!prominent}
        />
        <Plus className="size-4" aria-hidden />
        {prominent ? <span>{label ?? t('addItem')}</span> : null}
        <span className="h-px flex-1 bg-border group-hover/insert:bg-primary" aria-hidden />
      </button>
    </ItemTypePicker>
  );
}
