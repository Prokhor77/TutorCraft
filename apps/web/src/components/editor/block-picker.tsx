'use client';
import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { BLOCK_KIND_ICONS, type BlockKind } from './block-kinds';

type BlockPickerProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  kinds: readonly BlockKind[];
  onPick: (kind: BlockKind) => void;
  children: ReactNode;
};

/** Searchable block type picker ("+" button and "/" command). */
export function BlockPicker({ open, onOpenChange, kinds, onPick, children }: BlockPickerProps) {
  const t = useTranslations('editor');
  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent align="start" className="w-72 p-0">
        <Command label={t('pickBlock')}>
          <CommandInput placeholder={t('searchBlocks')} autoFocus />
          <CommandList>
            <CommandEmpty>{t('noBlocks')}</CommandEmpty>
            <CommandGroup heading={t('blocks')}>
              {kinds.map((kind) => {
                const Icon = BLOCK_KIND_ICONS[kind];
                return (
                  <CommandItem
                    key={kind}
                    value={`${kind} ${t(`kinds.${kind}`)}`}
                    onSelect={() => {
                      onOpenChange(false);
                      onPick(kind);
                    }}
                  >
                    <Icon className="size-4 text-text-muted" aria-hidden />
                    {t(`kinds.${kind}`)}
                  </CommandItem>
                );
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
