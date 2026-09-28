import { ChevronRight } from 'lucide-react';
import Link from 'next/link';
import { DueLabel, ItemTypeIcon, StatusBadge } from '@/components/course/item-meta';
import { ROUTES } from '@/features/auth/routes';
import type { TaskEntry } from '@/lib/api/schemas/me';

/** One deadline row; deep-links straight to the item (AC-9). */
export function TaskRow({ task }: { task: TaskEntry }) {
  return (
    <Link
      href={ROUTES.item(task.courseId, task.itemId)}
      className="flex items-center gap-3 rounded px-3 py-3 transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
    >
      <ItemTypeIcon type={task.itemType} />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-sm font-medium">{task.itemTitle}</span>
        <span className="truncate text-xs text-text-muted">{task.courseTitle}</span>
      </span>
      <span className="flex shrink-0 flex-col items-end gap-1">
        <DueLabel dueAt={task.dueAt} />
        <StatusBadge status={task.status} />
      </span>
      <ChevronRight className="size-4 shrink-0 text-text-muted" aria-hidden />
    </Link>
  );
}
