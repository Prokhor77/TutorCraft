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
import { ITEM_TYPES, type ItemType } from '@/lib/api/schemas/common';
import { ITEM_TYPE_ICONS } from './item-meta';

const RESOURCE_TYPES: readonly ItemType[] = ['page', 'file', 'video', 'url', 'folder'];
const ACTIVITY_TYPES: readonly ItemType[] = ITEM_TYPES.filter(
  (type) => !RESOURCE_TYPES.includes(type),
);

/** "+" type picker with search (SPEC §10: выбор типа с поиском). */
export function ItemTypePicker({
  open,
  onOpenChange,
  onPick,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onPick: (type: ItemType) => void;
  children: ReactNode;
}) {
  const t = useTranslations('itemTypes');
  const tCourse = useTranslations('course');
  const renderGroup = (heading: string, types: readonly ItemType[]) => (
    <CommandGroup heading={heading}>
      {types.map((type) => {
        const Icon = ITEM_TYPE_ICONS[type];
        return (
          <CommandItem
            key={type}
            value={`${type} ${t(type)} ${t(`${type}Hint`)}`}
            onSelect={() => {
              onOpenChange(false);
              onPick(type);
            }}
          >
            <Icon className="size-4 text-primary" aria-hidden />
            <span className="flex flex-col">
              <span className="font-medium">{t(type)}</span>
              <span className="text-xs text-text-muted">{t(`${type}Hint`)}</span>
            </span>
          </CommandItem>
        );
      })}
    </CommandGroup>
  );
  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent align="start" className="w-80 p-0">
        <Command label={tCourse('pickType')}>
          <CommandInput placeholder={tCourse('searchTypes')} autoFocus />
          <CommandList>
            <CommandEmpty>{tCourse('noTypes')}</CommandEmpty>
            {renderGroup(tCourse('activities'), ACTIVITY_TYPES)}
            {renderGroup(tCourse('resources'), RESOURCE_TYPES)}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
