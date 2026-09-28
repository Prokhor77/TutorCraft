import { ChevronRight } from 'lucide-react';
import Link from 'next/link';
import { DueLabel, ItemTypeIcon, StatusBadge } from '@/components/course/item-meta';
import { ROUTES } from '@/features/auth/routes';
import type { TaskEntry } from '@/lib/api/schemas/me';

/** One deadline row (Stitch rounded list row); deep-links straight to the item (AC-9). */
export function TaskRow({ task }: { task: TaskEntry }) {
  return (
    <Link
      href={ROUTES.item(task.courseId, task.itemId)}
      className="group flex items-center gap-3 rounded-md border border-transparent bg-surface-muted/50 px-3 py-2.5 transition-[background-color,border-color,box-shadow] duration-fast hover:border-card-border-hover hover:bg-surface hover:shadow-sm focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 sm:px-4"
    >
      <ItemTypeIcon type={task.itemType} className="size-10 rounded-full" />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-sm font-semibold">{task.itemTitle}</span>
        <span className="truncate text-xs text-text-muted">{task.courseTitle}</span>
      </span>
      <span className="flex shrink-0 flex-col items-end gap-1">
        <StatusBadge status={task.status} />
        <DueLabel dueAt={task.dueAt} />
      </span>
      <ChevronRight
        className="size-4 shrink-0 text-outline transition-transform duration-fast group-hover:translate-x-0.5 group-hover:text-primary"
        aria-hidden
      />
    </Link>
  );
}
