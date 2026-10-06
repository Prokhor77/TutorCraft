'use client';
import type { UseQueryResult } from '@tanstack/react-query';
import { Layers } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { DueLabel, ItemTypeIcon } from '@/components/course/item-meta';
import { StatusChip } from '@/components/course/status-chip';
import { ItemFacts } from '@/components/items/item-facts';
import { ResourceView } from '@/components/items/resource-views';
import { Sheet, SheetContent } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Panel } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { useItem } from '@/features/items/use-item';
import type { CourseOutline, OutlineItem, OutlineModule } from '@/lib/api/schemas/courses';

/** Modules (nested ones included) and items of an outline. */
export function countMaterials(modules: readonly OutlineModule[]): {
  modules: number;
  items: number;
} {
  return modules.reduce(
    (sum, module) => {
      const nested = countMaterials(module.children);
      return {
        modules: sum.modules + 1 + nested.modules,
        items: sum.items + module.items.length + nested.items,
      };
    },
    { modules: 0, items: 0 },
  );
}

/**
 * Course structure for the platform administrator: every module and item, hidden and scheduled ones included, with
 * a read-only preview of an item's content in a side sheet. Nothing here edits the course.
 */
export function CourseMaterials({ outline }: { outline: UseQueryResult<CourseOutline> }) {
  const t = useTranslations('admin.course.materials');
  const tCommon = useTranslations('common');
  const [itemId, setItemId] = useState<string | null>(null);

  if (outline.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (outline.isError || !outline.data)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void outline.refetch()}
      />
    );
  const modules = outline.data.modules;
  if (modules.length === 0)
    return <EmptyState icon={Layers} title={t('emptyTitle')} description={t('emptyText')} />;

  return (
    <>
      <div className="flex flex-col gap-4">
        {modules.map((module) => (
          <ModulePanel key={module.id} module={module} onOpen={setItemId} />
        ))}
      </div>
      <ItemPreview itemId={itemId} onClose={() => setItemId(null)} />
    </>
  );
}

function ModulePanel({ module, onOpen }: { module: OutlineModule; onOpen: (id: string) => void }) {
  const t = useTranslations('admin.course.materials');
  return (
    <Panel
      title={module.title}
      description={t('moduleCount', { count: module.items.length })}
      actions={<StatusChip visibility={module.visibility} />}
    >
      {module.items.length > 0 ? (
        <ul className="-mx-2 flex flex-col">
          {module.items.map((item) => (
            <li key={item.id}>
              <ItemRow item={item} onOpen={onOpen} />
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-text-muted">{t('moduleEmpty')}</p>
      )}
      {module.children.length > 0 ? (
        <div className="flex flex-col gap-3 border-l-2 border-border pl-4">
          {module.children.map((child) => (
            <ModulePanel key={child.id} module={child} onOpen={onOpen} />
          ))}
        </div>
      ) : null}
    </Panel>
  );
}

function ItemRow({ item, onOpen }: { item: OutlineItem; onOpen: (id: string) => void }) {
  const t = useTranslations('admin.course.materials');
  const tTypes = useTranslations('itemTypes');
  return (
    <button
      type="button"
      onClick={() => onOpen(item.id)}
      aria-label={t('open', { title: item.title })}
      className="flex w-full min-w-0 items-center gap-3 rounded px-2 py-2 text-left transition-colors duration-fast hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
    >
      <ItemTypeIcon type={item.type} />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate font-medium">{item.title}</span>
        <span className="flex flex-wrap items-center gap-x-2 text-xs text-text-muted">
          {tTypes(item.type)}
          <DueLabel dueAt={item.dueAt} />
        </span>
      </span>
      {item.visibility !== 'published' ? <StatusChip visibility={item.visibility} /> : null}
    </button>
  );
}

function ItemPreview({ itemId, onClose }: { itemId: string | null; onClose: () => void }) {
  const t = useTranslations('admin.course.materials');
  const tCommon = useTranslations('common');
  const tTypes = useTranslations('itemTypes');
  const item = useItem(itemId ?? '');
  const data = itemId ? item.data : undefined;
  return (
    <Sheet open={itemId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent
        title={data?.title ?? t('previewTitle')}
        description={data ? tTypes(data.type) : undefined}
        closeLabel={tCommon('close')}
        className="md:w-[48rem]"
      >
        {item.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
        {item.isError ? (
          <ErrorState
            title={t('previewError')}
            retryLabel={tCommon('retry')}
            onRetry={() => void item.refetch()}
          />
        ) : null}
        {data ? (
          <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip visibility={data.visibility} />
              <DueLabel dueAt={data.dueAt} />
            </div>
            <ItemFacts item={data} author />
            {data.content?.blocks.length || hasResource(data.type) ? (
              <ResourceView item={data} />
            ) : (
              <p className="text-sm text-text-muted">{t('noContent')}</p>
            )}
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

/** Types whose material lives in settings (file, link, folder, video) rather than in the block document. */
function hasResource(type: OutlineItem['type']): boolean {
  return type === 'file' || type === 'url' || type === 'folder' || type === 'video';
}
