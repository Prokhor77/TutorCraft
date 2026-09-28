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
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Layers, Plus } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { useCourseContext } from '@/features/courses/course-context';
import {
  applyItemMove,
  computeItemMove,
  reorderModules,
  studentPreview,
} from '@/features/courses/outline-moves';
import { useOutline, useOutlineMutations } from '@/features/courses/use-outline';
import { queryKeys } from '@/features/query-keys';
import { meApi } from '@/lib/api/endpoints/me';
import { PERMISSIONS } from '@/lib/access/permissions';
import type { ItemType } from '@/lib/api/schemas/common';
import type { CourseOutline, OutlineModule } from '@/lib/api/schemas/courses';
import { useUiStore } from '@/stores/ui-store';
import { QuickCreateItem } from '../quick-create-item';
import { BulkBar } from './bulk-bar';
import { MODULE_SORT_PREFIX, ModuleSection, type ModuleHandlers } from './module-section';
import { OutlineSidebar } from './outline-sidebar';

const DRAG_ACTIVATION_DISTANCE_PX = 6;
type PendingCreate = { moduleId: string; type: ItemType; position: number } | null;

export function CourseOutlineView() {
  const t = useTranslations('course');
  const tCommon = useTranslations('common');
  const queryClient = useQueryClient();
  const { course, editMode, can } = useCourseContext();
  const viewAsStudent = useUiStore((state) => state.viewAsStudent);
  const outline = useOutline(course.id);
  const completion = useQuery({
    queryKey: queryKeys.completion(course.id),
    queryFn: () => meApi.courseCompletion(course.id),
    enabled: !editMode && can(PERMISSIONS.submissionSubmit),
  });
  const mutations = useOutlineMutations(course.id);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [pendingCreate, setPendingCreate] = useState<PendingCreate>(null);
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: DRAG_ACTIVATION_DISTANCE_PX } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  if (outline.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (outline.isError || !outline.data)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void outline.refetch()}
      />
    );
  const data: CourseOutline = viewAsStudent ? studentPreview(outline.data) : outline.data;
  const setOutline = (next: CourseOutline) =>
    queryClient.setQueryData(queryKeys.outline(course.id), next);

  const handlers: ModuleHandlers = {
    isSelected: (id) => selected.has(id),
    onSelectItem: (id, value) =>
      setSelected((current) => {
        const next = new Set(current);
        if (value) next.add(id);
        else next.delete(id);
        return next;
      }),
    onRenameModule: (module, title) =>
      mutations.updateModule.mutate({ id: module.id, patch: { title, version: module.version } }),
    onToggleModuleVisibility: (module) =>
      mutations.updateModule.mutate({
        id: module.id,
        patch: {
          visibility: module.visibility === 'published' ? 'hidden' : 'published',
          version: module.version,
        },
      }),
    onDeleteModule: (module) =>
      mutations.deleteModule.mutate({ id: module.id, title: module.title }),
    onAddSubmodule: (module: OutlineModule) =>
      mutations.createModule.mutate({ title: t('newSubmoduleTitle'), parentId: module.id }),
    onRenameItem: (id, version, title) =>
      mutations.updateItem.mutate({ id, patch: { title, version } }),
    onToggleItemVisibility: (id, version, visible) =>
      mutations.updateItem.mutate({
        id,
        patch: { visibility: visible ? 'published' : 'hidden', version },
      }),
    onDuplicateItem: (id) => mutations.duplicateItem.mutate(id),
    onDeleteItem: (id, title) => mutations.deleteItem.mutate({ id, title }),
    onInsertItem: (moduleId, type, position) => setPendingCreate({ moduleId, type, position }),
  };

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over) return;
    const activeId = String(active.id);
    const overId = String(over.id);
    if (activeId.startsWith(MODULE_SORT_PREFIX)) {
      const moduleId = activeId.slice(MODULE_SORT_PREFIX.length);
      const result = reorderModules(data, moduleId, overId.replace(MODULE_SORT_PREFIX, ''));
      if (!result) return;
      setOutline(result.outline);
      mutations.moveModule.mutate({ id: moduleId, position: result.position });
      return;
    }
    const move = computeItemMove(data, activeId, overId);
    if (!move) return;
    setOutline(applyItemMove(data, move));
    mutations.moveItem.mutate({
      id: move.itemId,
      moduleId: move.moduleId,
      position: move.position,
    });
  };

  const addModule = () =>
    mutations.createModule.mutate({
      title: t('newModuleTitle', { number: data.modules.length + 1 }),
    });

  if (data.modules.length === 0) {
    return editMode ? (
      <EmptyState
        icon={Layers}
        title={t('emptyTeacherTitle')}
        description={t('emptyTeacherText')}
        action={
          <Button onClick={addModule} loading={mutations.createModule.isPending}>
            <Plus aria-hidden /> {t('createFirstModule')}
          </Button>
        }
      />
    ) : (
      <EmptyState
        icon={Layers}
        title={t('emptyStudentTitle')}
        description={t('emptyStudentText')}
      />
    );
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[16rem_minmax(0,1fr)]">
      <aside className="hidden lg:block">
        <OutlineSidebar modules={data.modules} coursePercent={completion.data?.percent ?? null} />
      </aside>
      <div className="flex min-w-0 flex-col gap-4">
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
          <SortableContext
            items={data.modules.map((module) => `${MODULE_SORT_PREFIX}${module.id}`)}
            strategy={verticalListSortingStrategy}
          >
            {data.modules.map((module) => (
              <ModuleSection
                key={module.id}
                courseId={course.id}
                module={module}
                editMode={editMode}
                handlers={handlers}
              />
            ))}
          </SortableContext>
        </DndContext>
        {editMode ? (
          <Button
            variant="secondary"
            onClick={addModule}
            loading={mutations.createModule.isPending}
            className="self-start"
          >
            <Plus aria-hidden /> {t('addModule')}
          </Button>
        ) : null}
        {editMode && selected.size > 0 ? (
          <BulkBar
            courseId={course.id}
            outline={data}
            selectedIds={[...selected]}
            onClear={() => setSelected(new Set())}
          />
        ) : null}
      </div>
      {pendingCreate ? (
        <QuickCreateItem
          courseId={course.id}
          moduleId={pendingCreate.moduleId}
          type={pendingCreate.type}
          position={pendingCreate.position}
          onClose={() => setPendingCreate(null)}
        />
      ) : null}
    </div>
  );
}
