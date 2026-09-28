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
import { FolderPlus, FolderTree, GripVertical, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { InlineEdit } from '@/components/ui/inline-edit';
import { Input, NativeSelect } from '@/components/ui/input';
import { SkeletonList } from '@/components/ui/skeleton';
import { Tooltip } from '@/components/ui/tooltip';
import {
  buildCategoryTree,
  descendantIds,
  type CategoryNode,
} from '@/features/admin/category-tree';
import { useCategories, useCategoryMutations } from '@/features/admin/use-admin';
import type { Category } from '@/lib/api/schemas/org';

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
      className="flex items-center gap-2 rounded-md border border-border bg-surface px-2 py-1.5"
    >
      <button
        type="button"
        className="cursor-grab touch-none rounded-xs p-1 text-text-muted"
        aria-label={t('drag', { name: node.name })}
        {...sortable.attributes}
        {...sortable.listeners}
      >
        <GripVertical className="size-4" aria-hidden />
      </button>
      <span className="min-w-0 flex-1 text-sm font-medium">
        <InlineEdit value={node.name} label={t('rename', { name: node.name })} onSave={onRename} />
      </span>
      <span className="text-xs text-text-muted">{t('courses', { count: node.courseCount })}</span>
      <NativeSelect
        aria-label={t('moveTo', { name: node.name })}
        className="h-8 w-40 text-xs"
        value={node.parentId ?? ROOT_VALUE}
        onChange={(event) => onMove(event.target.value === ROOT_VALUE ? null : event.target.value)}
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
      <ul className="flex flex-col gap-1.5">
        {nodes.map((node) => (
          <li key={node.id} className="flex flex-col gap-1.5">
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

  return (
    <div className="flex flex-col gap-4">
      <form
        className="flex gap-2"
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
          className="max-w-sm"
        />
        <Button type="submit" loading={mutations.create.isPending}>
          <FolderPlus aria-hidden /> {t('create')}
        </Button>
      </form>
      {categories.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {categories.isSuccess && all.length === 0 ? (
        <EmptyState icon={FolderTree} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SiblingList nodes={tree} all={all} mutations={mutations} />
      </DndContext>
    </div>
  );
}
