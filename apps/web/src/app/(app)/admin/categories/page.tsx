'use client';
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  Folder,
  FolderPlus,
  FolderTree,
  FolderX,
  Folders,
  GripVertical,
  Trash2,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { InlineEdit } from '@/components/ui/inline-edit';
import { Input, NativeSelect } from '@/components/ui/input';
import { Panel } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { Tooltip } from '@/components/ui/tooltip';
import {
  buildCategoryTree,
  descendantIds,
  type CategoryNode,
} from '@/features/admin/category-tree';
import { useCategories, useCategoryMutations } from '@/features/admin/use-admin';
import type { Category } from '@/lib/api/schemas/org';
import { cn } from '@/lib/utils/cn';

const DRAG_DISTANCE_PX = 6;
const ROOT_VALUE = '__root__';

function CategoryRow({
  node,
  all,
  onRename,
  onMove,
  onDelete,
  onAddChild,
}: {
  node: CategoryNode;
  all: Category[];
  onRename: (name: string) => void;
  onMove: (parentId: string | null) => void;
  onDelete: () => void;
  onAddChild: () => void;
}) {
  const t = useTranslations('adminCategories');
  const sortable = useSortable({ id: node.id });
  const blocked = descendantIds(all, node.id);
  return (
    <div
      ref={sortable.setNodeRef}
      style={{
        transform: CSS.Transform.toString(sortable.transform),
        transition: sortable.transition,
        marginLeft: `${node.depth * 1.5}rem`,
      }}
      className="flex flex-wrap items-center gap-2 rounded-md border border-transparent bg-surface-muted px-2 py-2 transition-[border-color,box-shadow] duration-fast hover:border-card-border-hover hover:bg-surface hover:shadow-sm sm:flex-nowrap sm:rounded-full sm:py-1.5 sm:pl-2 sm:pr-3"
    >
      <button
        type="button"
        className="cursor-grab touch-none rounded-full p-1.5 text-outline hover:bg-surface-container hover:text-text"
        aria-label={t('drag', { name: node.name })}
        {...sortable.attributes}
        {...sortable.listeners}
      >
        <GripVertical className="size-4" aria-hidden />
      </button>
      <span
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-full',
          node.depth === 0
            ? 'bg-primary-soft text-primary'
            : 'bg-surface-container text-text-muted',
        )}
      >
        <Folder className="size-4" aria-hidden />
      </span>
      <span className="min-w-0 flex-1 text-sm font-semibold">
        <InlineEdit value={node.name} label={t('rename', { name: node.name })} onSave={onRename} />
      </span>
      <Badge tone={node.courseCount > 0 ? 'success' : 'neutral'}>
        {t('courses', { count: node.courseCount })}
      </Badge>
      <span className="flex w-full items-center gap-1 sm:w-auto">
        <NativeSelect
          aria-label={t('moveTo', { name: node.name })}
          className="h-8 min-w-0 flex-1 rounded-full text-xs sm:w-44 sm:flex-none"
          value={node.parentId ?? ROOT_VALUE}
          onChange={(event) =>
            onMove(event.target.value === ROOT_VALUE ? null : event.target.value)
          }
        >
          <option value={ROOT_VALUE}>{t('root')}</option>
          {all
            .filter((candidate) => candidate.id !== node.id && !blocked.has(candidate.id))
            .map((candidate) => (
              <option key={candidate.id} value={candidate.id}>
                {candidate.name}
              </option>
            ))}
        </NativeSelect>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={t('addChild', { name: node.name })}
          onClick={onAddChild}
        >
          <FolderPlus aria-hidden />
        </Button>
        <Tooltip
          content={node.courseCount > 0 || node.children.length > 0 ? t('onlyEmpty') : t('delete')}
        >
          <span>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t('deleteNamed', { name: node.name })}
              disabled={node.courseCount > 0 || node.children.length > 0}
              onClick={onDelete}
            >
              <Trash2 aria-hidden />
            </Button>
          </span>
        </Tooltip>
      </span>
    </div>
  );
}

function SiblingList({
  nodes,
  all,
  mutations,
}: {
  nodes: CategoryNode[];
  all: Category[];
  mutations: ReturnType<typeof useCategoryMutations>;
}) {
  const t = useTranslations('adminCategories');
  return (
    <SortableContext items={nodes.map((node) => node.id)} strategy={verticalListSortingStrategy}>
      <ul className="flex flex-col gap-2">
        {nodes.map((node) => (
          <li key={node.id} className="flex flex-col gap-2">
            <CategoryRow
              node={node}
              all={all}
              onRename={(name) => mutations.update.mutate({ id: node.id, patch: { name } })}
              onMove={(parentId) =>
                mutations.update.mutate({ id: node.id, patch: { parentId, moveToParent: true } })
              }
              onDelete={() => mutations.remove.mutate(node.id)}
              onAddChild={() =>
                mutations.create.mutate({ name: t('newCategoryName'), parentId: node.id })
              }
            />
            {node.children.length > 0 ? (
              <SiblingList nodes={node.children} all={all} mutations={mutations} />
            ) : null}
          </li>
        ))}
      </ul>
    </SortableContext>
  );
}

/** FR-COURSE-01: category tree with drag & drop ordering and re-parenting. */
export default function AdminCategoriesPage() {
  const t = useTranslations('adminCategories');
  const tAdmin = useTranslations('admin');
  const tCommon = useTranslations('common');
  const categories = useCategories();
  const mutations = useCategoryMutations();
  const [name, setName] = useState('');
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: DRAG_DISTANCE_PX } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const all = categories.data ?? [];
  const tree = buildCategoryTree(all);

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const moving = all.find((category) => category.id === active.id);
    const target = all.find((category) => category.id === over.id);
    if (!moving || !target) return;
    const siblings = all
      .filter((category) => category.parentId === target.parentId)
      .sort((a, b) => a.position - b.position);
    const position = siblings.findIndex((category) => category.id === target.id);
    const reparent = moving.parentId !== target.parentId;
    mutations.update.mutate({
      id: moving.id,
      patch: { position, ...(reparent ? { parentId: target.parentId, moveToParent: true } : {}) },
    });
  };

  const roots = all.filter((category) => !category.parentId).length;
  const empty = all.filter(
    (category) =>
      category.courseCount === 0 && !all.some((child) => child.parentId === category.id),
  ).length;

  return (
    <div className="flex flex-col gap-gutter">
      {categories.isSuccess && all.length > 0 ? (
        <StatGrid className="lg:grid-cols-3">
          <StatCard label={t('statTotal')} icon={FolderTree} value={all.length} />
          <StatCard
            label={t('statRoots')}
            icon={Folders}
            tone="success"
            value={roots}
            footer={t('statRootsHint')}
          />
          <StatCard
            label={t('statEmpty')}
            icon={FolderX}
            tone="warning"
            value={empty}
            footer={t('statEmptyHint')}
          />
        </StatGrid>
      ) : null}
      <Panel
        title={tAdmin('nav.categories')}
        description={t('panelHint')}
        actions={
          <form
            className="flex w-full gap-2 sm:w-auto"
            onSubmit={(event) => {
              event.preventDefault();
              if (name.trim())
                mutations.create.mutate({ name: name.trim() }, { onSuccess: () => setName('') });
            }}
          >
            <Input
              aria-label={t('newCategory')}
              placeholder={t('newCategory')}
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="h-10 min-w-0 flex-1 rounded-full border-transparent bg-surface-muted sm:w-64"
            />
            <Button type="submit" loading={mutations.create.isPending}>
              <FolderPlus aria-hidden /> {t('create')}
            </Button>
          </form>
        }
      >
        {categories.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
        {categories.isSuccess && all.length === 0 ? (
          <EmptyState icon={FolderTree} title={t('emptyTitle')} description={t('emptyText')} />
        ) : null}
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
          <SiblingList nodes={tree} all={all} mutations={mutations} />
        </DndContext>
      </Panel>
    </div>
  );
}
