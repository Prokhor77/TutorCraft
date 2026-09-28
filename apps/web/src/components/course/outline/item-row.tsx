'use client';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  CheckCircle2,
  ExternalLink,
  Copy,
  Eye,
  EyeOff,
  GripVertical,
  MoreHorizontal,
  Settings2,
  Trash2,
} from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { InlineEdit } from '@/components/ui/inline-edit';
import { ROUTES } from '@/features/auth/routes';
import type { OutlineItem } from '@/lib/api/schemas/courses';
import { cn } from '@/lib/utils/cn';
import { formatDateTime } from '@/lib/utils/format';
import { DueLabel, ItemTypeIcon, LockedReason, StatusBadge } from '../item-meta';

type TeacherActions = {
  selected: boolean;
  onSelect: (selected: boolean) => void;
  onRename: (title: string) => void;
  onToggleVisibility: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
  /** Builder: select this item for the inspector pane. */
  inspected?: boolean;
  onInspect?: () => void;
};

export function StudentItemRow({ courseId, item }: { courseId: string; item: OutlineItem }) {
  const locked = !item.availability.available;
  const content = (
    <>
      <ItemTypeIcon type={item.type} className={locked ? 'opacity-60' : undefined} />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className={cn('truncate text-sm font-medium', locked && 'text-text-muted')}>
          {item.title}
        </span>
        {locked ? (
          <LockedReason reasons={item.availability.reasons} />
        ) : (
          <DueLabel dueAt={item.dueAt} />
        )}
      </span>
      <span className="flex shrink-0 items-center gap-1.5">
        <StatusBadge status={item.status === 'not_started' ? null : item.status} />
        {item.completion === 'complete' ? (
          <CheckCircle2 className="size-5 text-success" aria-label="✓" />
        ) : null}
      </span>
    </>
  );
  if (locked) return <div className="flex items-center gap-3 rounded px-3 py-2.5">{content}</div>;
  return (
    <Link
      href={ROUTES.item(courseId, item.id)}
      className="flex items-center gap-3 rounded px-3 py-2.5 transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
    >
      {content}
    </Link>
  );
}

/** Teacher row: inline rename (UX-04), drag handle, visibility, duplicate, delete with undo (UX-06). */
export function TeacherItemRow({
  courseId,
  item,
  actions,
}: {
  courseId: string;
  item: OutlineItem;
  actions: TeacherActions;
}) {
  const t = useTranslations('course');
  const locale = useLocale();
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
  });
  const hidden = item.visibility !== 'published';
  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        'group relative flex items-center gap-2 rounded bg-surface px-2 py-2 transition-[background-color,box-shadow] duration-fast hover:bg-accent/5',
        isDragging && 'z-10 scale-[1.02] shadow-lg ring-1 ring-accent/40',
        actions.selected && 'bg-primary-soft/50',
        actions.inspected && 'bg-accent/10 shadow-md ring-1 ring-accent/30',
      )}
    >
      <Checkbox
        checked={actions.selected}
        onCheckedChange={(checked) => actions.onSelect(checked === true)}
        aria-label={t('selectItem', { title: item.title })}
      />
      <button
        type="button"
        className="cursor-grab touch-none rounded-xs p-1 text-text-muted hover:text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring active:cursor-grabbing"
        aria-label={t('dragItem', { title: item.title })}
        {...attributes}
        {...listeners}
      >
        <GripVertical className="size-4" aria-hidden />
      </button>
      <ItemTypeIcon type={item.type} className={hidden ? 'opacity-50' : undefined} />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <InlineEdit
          value={item.title}
          label={t('renameItem', { title: item.title })}
          onSave={actions.onRename}
          className="text-sm font-medium"
        >
          {actions.onInspect ? (
            <button
              type="button"
              onClick={actions.onInspect}
              aria-pressed={actions.inspected}
              className={cn(
                'truncate rounded-sm text-left hover:text-primary',
                hidden && 'text-text-muted',
                actions.inspected && 'text-primary',
              )}
            >
              {item.title}
            </button>
          ) : (
            <Link
              href={ROUTES.item(courseId, item.id)}
              className={cn('truncate hover:underline', hidden && 'text-text-muted')}
            >
              {item.title}
            </Link>
          )}
        </InlineEdit>
        <span className="flex flex-wrap items-center gap-2">
          <DueLabel dueAt={item.dueAt} />
          {item.visibility === 'scheduled' && item.publishAt ? (
            <Badge tone="info">
              {t('scheduledFor', { date: formatDateTime(item.publishAt, locale) })}
            </Badge>
          ) : null}
          {item.visibility === 'hidden' ? <Badge tone="neutral">{t('hiddenBadge')}</Badge> : null}
          {!item.availability.available ? (
            <LockedReason reasons={item.availability.reasons} />
          ) : null}
        </span>
      </div>
      {actions.onInspect ? (
        // Below `sm` «open» and visibility live in the inspector sheet to give the title room.
        <Button asChild variant="ghost" size="icon-sm" className="hidden sm:inline-flex">
          <Link
            href={ROUTES.item(courseId, item.id)}
            aria-label={t('openItem', { title: item.title })}
          >
            <ExternalLink aria-hidden />
          </Link>
        </Button>
      ) : null}
      <Button
        variant="ghost"
        size="icon-sm"
        className={actions.onInspect ? 'hidden sm:inline-flex' : undefined}
        onClick={actions.onToggleVisibility}
        aria-label={
          hidden ? t('showItem', { title: item.title }) : t('hideItem', { title: item.title })
        }
      >
        {hidden ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
      </Button>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t('itemActions', { title: item.title })}
          >
            <MoreHorizontal aria-hidden />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem asChild>
            <Link href={ROUTES.itemSettings(courseId, item.id)}>
              <Settings2 aria-hidden /> {t('itemSettings')}
            </Link>
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={actions.onDuplicate}>
            <Copy aria-hidden /> {t('duplicate')}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem tone="danger" onSelect={actions.onDelete}>
            <Trash2 aria-hidden /> {t('delete')}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
