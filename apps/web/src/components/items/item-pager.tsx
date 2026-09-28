'use client';
import { ArrowLeft, ArrowRight, List, Lock } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { ITEM_TYPE_ICONS } from '@/components/course/item-meta';
import { Button } from '@/components/ui/button';
import { ROUTES } from '@/features/auth/routes';
import { flattenModules } from '@/features/courses/use-outline';
import type { CourseOutline, OutlineItem } from '@/lib/api/schemas/courses';
import { cn } from '@/lib/utils/cn';

/** Items a learner sees in the outline, in reading order (same filter as the student module list). */
function learnerSequence(outline: CourseOutline | undefined): OutlineItem[] {
  return flattenModules(outline?.modules ?? []).flatMap((module) =>
    module.items.filter(
      (item) => item.availability.available || item.availability.mode === 'show_locked',
    ),
  );
}

function PagerCard({
  courseId,
  item,
  direction,
}: {
  courseId: string;
  item: OutlineItem;
  direction: 'prev' | 'next';
}) {
  const t = useTranslations('itemView');
  const Icon = item.availability.available ? ITEM_TYPE_ICONS[item.type] : Lock;
  const next = direction === 'next';
  return (
    <Link
      href={ROUTES.item(courseId, item.id)}
      className={cn(
        'group flex min-w-0 flex-1 items-center gap-3 rounded-lg border border-card-border bg-surface p-4 shadow-sm transition-colors hover:border-primary/40 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/25',
        next && 'flex-row-reverse text-right',
      )}
    >
      {next ? (
        <ArrowRight className="size-5 shrink-0 text-primary" aria-hidden />
      ) : (
        <ArrowLeft className="size-5 shrink-0 text-primary" aria-hidden />
      )}
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-xs text-text-muted">{t(next ? 'nextItem' : 'prevItem')}</span>
        <span
          className={cn(
            'inline-flex min-w-0 items-center gap-1.5 font-semibold group-hover:text-primary',
            next && 'flex-row-reverse',
          )}
        >
          <Icon className="size-4 shrink-0" aria-hidden />
          <span className="truncate">{item.title}</span>
        </span>
      </span>
    </Link>
  );
}

/** Bottom navigation of the learner reader: previous / next item and a way back to the course outline. */
export function ItemPager({
  courseId,
  itemId,
  outline,
}: {
  courseId: string;
  itemId: string;
  outline: CourseOutline | undefined;
}) {
  const t = useTranslations('itemView');
  const sequence = learnerSequence(outline);
  const index = sequence.findIndex((item) => item.id === itemId);
  const prev = index > 0 ? sequence[index - 1] : undefined;
  const next = index >= 0 ? sequence[index + 1] : undefined;
  return (
    <nav aria-label={t('pagerLabel')} className="flex flex-col gap-3">
      {prev || next ? (
        <div className="flex flex-col gap-3 sm:flex-row">
          {prev ? (
            <PagerCard courseId={courseId} item={prev} direction="prev" />
          ) : (
            <span className="hidden flex-1 sm:block" />
          )}
          {next ? (
            <PagerCard courseId={courseId} item={next} direction="next" />
          ) : (
            <span className="hidden flex-1 sm:block" />
          )}
        </div>
      ) : null}
      <div className="flex justify-center">
        <Button asChild variant="ghost" size="sm">
          <Link href={ROUTES.course(courseId)}>
            <List aria-hidden /> {t('backToOutline')}
          </Link>
        </Button>
      </div>
    </nav>
  );
}
