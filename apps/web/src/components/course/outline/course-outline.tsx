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
import { Layers, Plus, Search } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import { Segmented } from '@/components/ui/segmented';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { BREAKPOINTS, useMediaQuery } from '@/features/app/use-media-query';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import {
  applyItemMove,
  computeItemMove,
  filterOutlineByTitle,
  moduleProgress,
  publishedProgress,
  reorderModules,
  studentPreview,
} from '@/features/courses/outline-moves';
import { findItem, useOutline, useOutlineMutations } from '@/features/courses/use-outline';
import { queryKeys } from '@/features/query-keys';
import { meApi } from '@/lib/api/endpoints/me';
import { PERMISSIONS } from '@/lib/access/permissions';
import { ITEM_TYPES, type ItemType } from '@/lib/api/schemas/common';
import type { CourseOutline, OutlineModule } from '@/lib/api/schemas/courses';
import { cn } from '@/lib/utils/cn';
import { useUiStore } from '@/stores/ui-store';
import { QuickCreateItem } from '../quick-create-item';
import { BulkBar } from './bulk-bar';
import { MODULE_SORT_PREFIX, ModuleSection, type ModuleHandlers } from './module-section';
import { CourseTree } from './course-tree';
import { ItemInspector } from './item-inspector';
import { CanvasAddBar, ItemWorkspace } from './item-workspace';

const DRAG_ACTIVATION_DISTANCE_PX = 6;
const INSPECT_PARAM = 'item';
const TYPE_PARAM = 'type';
type BuilderMode = 'builder' | 'structure' | 'preview';

/** Learner «Тесты» view: only items of one type (e.g. `?type=quiz`). */
function filterByType(outline: CourseOutline, type: ItemType): CourseOutline {
  const keep = (modules: OutlineModule[]): OutlineModule[] =>
    modules
      .map((module) => ({
        ...module,
        items: module.items.filter((item) => item.type === type),
        children: keep(module.children),
      }))
      .filter((module) => module.items.length > 0 || module.children.length > 0);
  return { ...outline, modules: keep(outline.modules) };
}
type PendingCreate = { moduleId: string; type: ItemType; position: number } | null;

const PERCENT = 100;

/** Stitch mobile builder: readiness card + horizontally scrolling module chips («Все · Модуль 1 (3/3) …»). */
function MobileCourseSummary({
  modules,
  percent,
  done,
  total,
  learner,
}: {
  modules: OutlineModule[];
  percent: number;
  done: number;
  total: number;
  learner: boolean;
}) {
  const t = useTranslations('builder');
  return (
    <div className="flex flex-col gap-3 md:hidden">
      <Card className="flex flex-col gap-2 bg-gradient-to-br from-surface to-primary-soft/60 p-4">
        <p className="flex justify-between gap-2 text-xs text-text-muted">
          <span>{learner ? t('completedLabel') : t('readinessLabel')}</span>
          <span className="font-semibold text-primary">
            {t('ofTotal', { done, total })} ({percent}%)
          </span>
        </p>
        <Progress
          value={percent}
          tone={percent >= PERCENT ? 'success' : 'primary'}
          label={learner ? t('completedLabel') : t('readinessLabel')}
        />
      </Card>
      <nav aria-label={t('modulesNav')} className="-mx-page-x overflow-x-auto px-page-x">
        <ul className="flex gap-2">
          {modules.map((module, index) => {
            const count = learner ? moduleProgress(module) : publishedProgress([module]);
            return (
              <li key={module.id}>
                <a
                  href={`#module-${module.id}`}
                  className="flex h-9 items-center gap-1.5 whitespace-nowrap rounded-full border border-card-border bg-surface px-3.5 text-label-lg shadow-sm hover:border-card-border-hover"
                >
                  {t('moduleChip', { number: index + 1 })}
                  <span className="text-label-md text-text-muted">
                    {count.done}/{count.total}
                  </span>
                </a>
              </li>
            );
          })}
        </ul>
      </nav>
    </div>
  );
}

/**
 * Stitch tree header card: «Структура курса» with readiness (authors: published share; learners: completion),
 * search over modules/items and «Добавить модуль».
 */
function TreePanelHeader({
  percent,
  done,
  total,
  learner,
  query,
  onQuery,
  onAddModule,
  adding,
  compact,
}: {
  percent: number;
  done: number;
  total: number;
  learner: boolean;
  query: string;
  onQuery: (value: string) => void;
  onAddModule?: () => void;
  adding?: boolean;
  compact: boolean;
}) {
  const t = useTranslations('builder');
  const tCourse = useTranslations('course');
  return (
    <Card className={cn('flex flex-col gap-3 p-4', compact && 'hidden xl:flex')}>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg">{learner ? t('myProgress') : t('tree')}</h2>
        <span
          className={cn(
            'rounded-full px-2.5 py-0.5 text-label-sm uppercase',
            percent >= PERCENT ? 'bg-success-soft text-success' : 'bg-primary-soft text-primary',
          )}
        >
          {t('readyChip', { percent })}
        </span>
      </div>
      <div className="flex flex-col gap-1.5">
        <p className="flex justify-between gap-2 text-xs text-text-muted">
          <span>{learner ? t('completedLabel') : t('readinessLabel')}</span>
          <span className="font-semibold text-text">{t('ofTotal', { done, total })}</span>
        </p>
        <Progress
          value={percent}
          tone={percent >= PERCENT ? 'success' : 'primary'}
          label={learner ? tCourse('courseProgress', { percent }) : t('readinessLabel')}
        />
      </div>
      <div className="relative">
        <Search
          className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-text-muted"
          aria-hidden
        />
        <Input
          type="search"
          value={query}
          onChange={(event) => onQuery(event.target.value)}
          placeholder={t('searchTree')}
          aria-label={t('searchTree')}
          className="h-10 pl-10"
        />
      </div>
      {onAddModule ? (
        <Button variant="secondary" onClick={onAddModule} loading={adding}>
          <Plus aria-hidden /> {tCourse('addModule')}
        </Button>
      ) : null}
    </Card>
  );
}

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
  const tBuilder = useTranslations('builder');
  const tTypes = useTranslations('itemTypes');
  const router = useRouter();
  const searchParams = useSearchParams();
  const isDesktop = useMediaQuery(BREAKPOINTS.desktop, true);
  const setViewAsStudent = useUiStore((state) => state.setViewAsStudent);
  const [mobileMode, setMobileMode] = useState<Exclude<BuilderMode, 'preview'>>('builder');
  const [treeQuery, setTreeQuery] = useState('');
  const isAuthor = can(PERMISSIONS.courseEdit);
  const inspectedId = searchParams.get(INSPECT_PARAM);
  const typeParam = searchParams.get(TYPE_PARAM);
  const typeFilter =
    typeParam && (ITEM_TYPES as readonly string[]).includes(typeParam)
      ? (typeParam as ItemType)
      : null;
  const inspect = (itemId: string | null) => {
    if (itemId) setMobileMode('builder');
    const params = new URLSearchParams(searchParams.toString());
    if (itemId) params.set(INSPECT_PARAM, itemId);
    else params.delete(INSPECT_PARAM);
    const query = params.toString();
    router.replace(query ? `?${query}` : ROUTES.course(course.id), { scroll: false });
  };
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
    inspectedId,
    onInspectItem: inspect,
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

  const isMobileStructure = isAuthor && !viewAsStudent && mobileMode === 'structure';
  const baseModules = typeFilter ? filterByType(data, typeFilter).modules : data.modules;
  const treeModules = filterOutlineByTitle(baseModules, treeQuery);
  const learner = !editMode;
  const readiness = learner
    ? {
        percent: Math.round(completion.data?.percent ?? 0),
        ...(() => {
          const items = Object.values(completion.data?.items ?? {});
          return {
            done: items.filter((state) => state === 'complete').length,
            total: items.length,
          };
        })(),
      }
    : (() => {
        const { done, total } = publishedProgress(data.modules);
        return { done, total, percent: total ? Math.round((done / total) * PERCENT) : 0 };
      })();
  const workspaceItem = editMode && inspectedId ? findItem(data, inspectedId) : null;
  const addAfterWorkspace = (type: ItemType) => {
    if (!workspaceItem) return;
    const index = workspaceItem.module.items.findIndex((entry) => entry.id === inspectedId);
    setPendingCreate({ moduleId: workspaceItem.module.id, type, position: index + 1 });
  };

  return (
    <div className="flex flex-col gap-4">
      {isAuthor ? (
        <div className="md:hidden">
          <Segmented<BuilderMode>
            className="w-full"
            label={tBuilder('modes')}
            value={viewAsStudent ? 'preview' : mobileMode}
            onChange={(mode) => {
              setViewAsStudent(mode === 'preview');
              if (mode !== 'preview') setMobileMode(mode);
            }}
            options={[
              { value: 'builder', label: tBuilder('modeBuilder') },
              { value: 'structure', label: tBuilder('modeStructure') },
              { value: 'preview', label: tBuilder('modePreview') },
            ]}
          />
        </div>
      ) : null}
      {typeFilter ? (
        <div className="flex flex-wrap items-center gap-2 text-sm text-text-muted">
          {tBuilder('filtered', { type: tTypes(typeFilter) })}
          <Button
            variant="secondary"
            size="sm"
            onClick={() => router.replace(ROUTES.course(course.id), { scroll: false })}
          >
            {tBuilder('clearFilter')}
          </Button>
        </div>
      ) : null}
      <div className="flex items-start gap-gutter">
        <aside
          aria-label={tBuilder('tree')}
          className={cn(
            'shrink-0 md:sticky md:top-[calc(var(--size-header)+1rem)] md:block md:max-h-[calc(100dvh-var(--size-header)-2rem)] md:w-20 md:overflow-y-auto xl:w-tree',
            isMobileStructure ? 'block w-full' : 'hidden',
          )}
        >
          <div className="flex flex-col gap-3">
            {learner && !completion.data ? null : (
              <TreePanelHeader
                {...readiness}
                learner={learner}
                query={treeQuery}
                onQuery={setTreeQuery}
                onAddModule={editMode ? addModule : undefined}
                adding={mutations.createModule.isPending}
                compact={!isMobileStructure}
              />
            )}
            <CourseTree
              modules={treeModules}
              variant={isMobileStructure ? 'full' : 'responsive'}
              audience={editMode ? 'author' : 'learner'}
              activeItemId={editMode ? inspectedId : null}
              onSelectItem={editMode ? inspect : undefined}
              hrefFor={editMode ? undefined : (itemId) => ROUTES.item(course.id, itemId)}
            />
          </div>
        </aside>
        <div className={cn('min-w-0 flex-1', isMobileStructure && 'hidden md:block')}>
          {!workspaceItem ? (
            <MobileCourseSummary modules={data.modules} {...readiness} learner={learner} />
          ) : null}
          {workspaceItem && inspectedId ? (
            <div className="mx-auto flex w-full max-w-canvas flex-col gap-4">
              <ItemWorkspace
                key={inspectedId}
                courseId={course.id}
                itemId={inspectedId}
                onBack={() => inspect(null)}
              />
              {!isDesktop ? (
                <Card className="p-5">
                  <h2 className="mb-4 text-lg">{tBuilder('inspector')}</h2>
                  <ItemInspector itemId={inspectedId} courseId={course.id} />
                </Card>
              ) : null}
              <CanvasAddBar onAdd={addAfterWorkspace} />
            </div>
          ) : (
            <div className="mx-auto flex w-full max-w-canvas flex-col gap-4">
              <DndContext
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragEnd={onDragEnd}
              >
                <SortableContext
                  items={data.modules.map((module) => `${MODULE_SORT_PREFIX}${module.id}`)}
                  strategy={verticalListSortingStrategy}
                >
                  {(typeFilter ? filterByType(data, typeFilter) : data).modules.map((module) => (
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
          )}
        </div>
        {editMode && isDesktop ? (
          <aside
            aria-label={tBuilder('inspector')}
            className="sticky top-[calc(var(--size-header)+1rem)] w-inspector shrink-0"
          >
            <Card className="max-h-[calc(100dvh-var(--size-header)-2rem)] overflow-y-auto p-5">
              <ItemInspector itemId={inspectedId} courseId={course.id} />
            </Card>
          </aside>
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
