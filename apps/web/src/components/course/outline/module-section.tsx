'use client';
import * as Collapsible from '@radix-ui/react-collapsible';
import { useDroppable } from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  ChevronDown,
  Eye,
  EyeOff,
  FolderPlus,
  GripVertical,
  MoreHorizontal,
  Trash2,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Fragment, useState } from 'react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { InlineEdit } from '@/components/ui/inline-edit';
import { Progress } from '@/components/ui/progress';
import { MODULE_DROP_PREFIX, moduleProgress } from '@/features/courses/outline-moves';
import type { ItemType } from '@/lib/api/schemas/common';
import type { OutlineModule } from '@/lib/api/schemas/courses';
import { cn } from '@/lib/utils/cn';
import { LockedReason } from '../item-meta';
import { InsertItemButton } from './insert-item-button';
import { StudentItemRow, TeacherItemRow } from './item-row';

export const MODULE_SORT_PREFIX = 'mod:';

export type ModuleHandlers = {
  isSelected: (itemId: string) => boolean;
  onSelectItem: (itemId: string, selected: boolean) => void;
  onRenameModule: (module: OutlineModule, title: string) => void;
  onToggleModuleVisibility: (module: OutlineModule) => void;
  onDeleteModule: (module: OutlineModule) => void;
  onAddSubmodule: (module: OutlineModule) => void;
  onRenameItem: (itemId: string, version: number, title: string) => void;
  onToggleItemVisibility: (itemId: string, version: number, visible: boolean) => void;
  onDuplicateItem: (itemId: string) => void;
  onDeleteItem: (itemId: string, title: string) => void;
  onInsertItem: (moduleId: string, type: ItemType, position: number) => void;
};

type Props = {
  courseId: string;
  module: OutlineModule;
  editMode: boolean;
  handlers: ModuleHandlers;
  nested?: boolean;
};

function ModuleBody({ courseId, module, editMode, handlers }: Omit<Props, 'nested'>) {
  const t = useTranslations('course');
  const { setNodeRef, isOver } = useDroppable({
    id: `${MODULE_DROP_PREFIX}${module.id}`,
    disabled: !editMode,
  });
  if (!editMode) {
    const visible = module.items.filter(
      (item) => item.availability.available || item.availability.mode === 'show_locked',
    );
    if (visible.length === 0)
      return <p className="px-3 py-2 text-sm text-text-muted">{t('moduleEmptyStudent')}</p>;
    return (
      <ul className="flex flex-col">
        {visible.map((item) => (
          <li key={item.id}>
            <StudentItemRow courseId={courseId} item={item} />
          </li>
        ))}
      </ul>
    );
  }
  return (
    <div
      ref={setNodeRef}
      className={cn('flex flex-col rounded-md', isOver && 'bg-primary-soft/40')}
    >
      <SortableContext
        items={module.items.map((item) => item.id)}
        strategy={verticalListSortingStrategy}
      >
        {module.items.length === 0 ? (
          <InsertItemButton
            prominent
            label={t('addFirstItem')}
            onPick={(type) => handlers.onInsertItem(module.id, type, 0)}
          />
        ) : (
          <InsertItemButton onPick={(type) => handlers.onInsertItem(module.id, type, 0)} />
        )}
        {module.items.map((item, index) => (
          <Fragment key={item.id}>
            <TeacherItemRow
              courseId={courseId}
              item={item}
              actions={{
                selected: handlers.isSelected(item.id),
                onSelect: (selected) => handlers.onSelectItem(item.id, selected),
                onRename: (title) => handlers.onRenameItem(item.id, item.version, title),
                onToggleVisibility: () =>
                  handlers.onToggleItemVisibility(
                    item.id,
                    item.version,
                    item.visibility !== 'published',
                  ),
                onDuplicate: () => handlers.onDuplicateItem(item.id),
                onDelete: () => handlers.onDeleteItem(item.id, item.title),
              }}
            />
            {index < module.items.length - 1 ? (
              <InsertItemButton
                onPick={(type) => handlers.onInsertItem(module.id, type, index + 1)}
              />
            ) : null}
          </Fragment>
        ))}
      </SortableContext>
      {module.items.length > 0 ? (
        <div className="pt-1">
          <InsertItemButton
            prominent
            onPick={(type) => handlers.onInsertItem(module.id, type, module.items.length)}
          />
        </div>
      ) : null}
    </div>
  );
}

/** Collapsible module with inline rename, visibility, drag handle and nested submodules (FR-COURSE-03). */
export function ModuleSection({ courseId, module, editMode, handlers, nested }: Props) {
  const t = useTranslations('course');
  const [open, setOpen] = useState(true);
  const sortable = useSortable({
    id: `${MODULE_SORT_PREFIX}${module.id}`,
    disabled: !editMode || nested,
  });
  const { done, total } = moduleProgress(module);
  const hidden = module.visibility !== 'published';
  const locked = !module.availability.available;

  return (
    <Collapsible.Root open={open} onOpenChange={setOpen} asChild>
      <section
        id={`module-${module.id}`}
        ref={sortable.setNodeRef}
        style={{
          transform: CSS.Transform.toString(sortable.transform),
          transition: sortable.transition,
        }}
        aria-labelledby={`module-title-${module.id}`}
        className={cn(
          'scroll-mt-24 rounded-lg border border-border bg-surface shadow-sm',
          nested && 'border-dashed shadow-none',
          sortable.isDragging && 'z-10 shadow-lg',
        )}
      >
        <div className="flex items-center gap-2 border-b border-border px-3 py-3">
          {editMode && !nested ? (
            <button
              type="button"
              className="cursor-grab touch-none rounded-xs p-1 text-text-muted"
              aria-label={t('dragModule', { title: module.title })}
              {...sortable.attributes}
              {...sortable.listeners}
            >
              <GripVertical className="size-4" aria-hidden />
            </button>
          ) : null}
          <Collapsible.Trigger asChild>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={
                open ? t('collapse', { title: module.title }) : t('expand', { title: module.title })
              }
            >
              <ChevronDown
                className={cn('transition-transform duration-fast', !open && '-rotate-90')}
                aria-hidden
              />
            </Button>
          </Collapsible.Trigger>
          <h2
            id={`module-title-${module.id}`}
            className={cn('min-w-0 flex-1 text-base font-semibold', hidden && 'text-text-muted')}
          >
            <InlineEdit
              value={module.title}
              label={t('renameModule', { title: module.title })}
              disabled={!editMode}
              onSave={(title) => handlers.onRenameModule(module, title)}
            />
          </h2>
          {total > 0 ? (
            <span className="hidden w-28 flex-col gap-1 sm:flex">
              <span className="text-right text-xs text-text-muted">
                {t('moduleProgress', { done, total })}
              </span>
              <Progress
                value={(done / total) * 100}
                label={t('moduleProgress', { done, total })}
                tone={done === total ? 'success' : 'primary'}
              />
            </span>
          ) : null}
          {editMode ? (
            <>
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() => handlers.onToggleModuleVisibility(module)}
                aria-label={
                  hidden
                    ? t('showModule', { title: module.title })
                    : t('hideModule', { title: module.title })
                }
              >
                {hidden ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={t('moduleActions', { title: module.title })}
                  >
                    <MoreHorizontal aria-hidden />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  {!nested ? (
                    <DropdownMenuItem onSelect={() => handlers.onAddSubmodule(module)}>
                      <FolderPlus aria-hidden /> {t('addSubmodule')}
                    </DropdownMenuItem>
                  ) : null}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem tone="danger" onSelect={() => handlers.onDeleteModule(module)}>
                    <Trash2 aria-hidden /> {t('deleteModule')}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </>
          ) : null}
        </div>
        <Collapsible.Content className="flex flex-col gap-3 p-2">
          {locked && !editMode ? (
            <div className="px-3 py-2">
              <LockedReason reasons={module.availability.reasons} />
            </div>
          ) : (
            <ModuleBody
              courseId={courseId}
              module={module}
              editMode={editMode}
              handlers={handlers}
            />
          )}
          {module.children.map((child) => (
            <ModuleSection
              key={child.id}
              courseId={courseId}
              module={child}
              editMode={editMode}
              handlers={handlers}
              nested
            />
          ))}
        </Collapsible.Content>
      </section>
    </Collapsible.Root>
  );
}
