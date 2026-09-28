'use client';
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from '@/components/ui/toast';
import {
  coursesApi,
  type CreateItemInput,
  type ItemPatch,
  type ModulePatch,
} from '@/lib/api/endpoints/courses';
import type { CourseOutline, OutlineItem, OutlineModule } from '@/lib/api/schemas/courses';
import { queryKeys } from '../query-keys';

export function useOutline(courseId: string) {
  return useQuery({
    queryKey: queryKeys.outline(courseId),
    queryFn: () => coursesApi.outline(courseId),
  });
}

export function useTrash(courseId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.trash(courseId),
    queryFn: () => coursesApi.trash(courseId),
    enabled,
  });
}

function invalidateCourseContent(queryClient: QueryClient, courseId: string) {
  void queryClient.invalidateQueries({ queryKey: queryKeys.outline(courseId) });
  void queryClient.invalidateQueries({ queryKey: queryKeys.trash(courseId) });
  void queryClient.invalidateQueries({ queryKey: queryKeys.myTasks });
}

/** Walks modules (incl. one level of children) — used for optimistic outline updates. */
export function flattenModules(modules: OutlineModule[]): OutlineModule[] {
  return modules.flatMap((module) => [module, ...flattenModules(module.children)]);
}

export function findItem(
  outline: CourseOutline | undefined,
  itemId: string,
): { item: OutlineItem; module: OutlineModule } | null {
  for (const owner of flattenModules(outline?.modules ?? [])) {
    const item = owner.items.find((candidate) => candidate.id === itemId);
    if (item) return { item, module: owner };
  }
  return null;
}

function patchOutline(
  queryClient: QueryClient,
  courseId: string,
  update: (outline: CourseOutline) => CourseOutline,
) {
  queryClient.setQueryData<CourseOutline>(queryKeys.outline(courseId), (current) =>
    current ? update(current) : current,
  );
}

function mapModules(
  modules: OutlineModule[],
  fn: (module: OutlineModule) => OutlineModule,
): OutlineModule[] {
  return modules.map((module) => fn({ ...module, children: mapModules(module.children, fn) }));
}

/** All structure mutations for the course editor (FR-COURSE-03..07). */
export function useOutlineMutations(courseId: string) {
  const queryClient = useQueryClient();
  const tUndo = useTranslations('undo');
  const invalidate = () => invalidateCourseContent(queryClient, courseId);

  const createModule = useMutation({
    mutationFn: (input: { title: string; parentId?: string | null }) =>
      coursesApi.createModule(courseId, input),
    onSettled: invalidate,
  });

  const updateModule = useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: ModulePatch }) =>
      coursesApi.updateModule(id, patch),
    onMutate: ({ id, patch }) =>
      patchOutline(queryClient, courseId, (outline) => ({
        ...outline,
        modules: mapModules(outline.modules, (module) =>
          module.id === id
            ? { ...module, ...stripUndefined(patch), version: module.version }
            : module,
        ),
      })),
    onSettled: invalidate,
  });

  const moveModule = useMutation({
    mutationFn: ({
      id,
      position,
      parentId,
    }: {
      id: string;
      position: number;
      parentId?: string | null;
    }) => coursesApi.moveModule(id, { position, parentId }),
    onSettled: invalidate,
  });

  const deleteModule = useMutation({
    mutationFn: (module: { id: string; title: string }) => coursesApi.deleteModule(module.id),
    onSuccess: (_result, module) =>
      toast({
        title: tUndo('moduleDeleted', { title: module.title }),
        action: {
          label: tUndo('undo'),
          onClick: () => void coursesApi.restoreModule(module.id).then(invalidate),
        },
      }),
    onSettled: invalidate,
  });

  const createItem = useMutation({
    mutationFn: ({ moduleId, input }: { moduleId: string; input: CreateItemInput }) =>
      coursesApi.createItem(moduleId, input),
    onSettled: invalidate,
  });

  const updateItem = useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: ItemPatch }) =>
      coursesApi.updateItem(id, patch),
    onMutate: ({ id, patch }) =>
      patchOutline(queryClient, courseId, (outline) => ({
        ...outline,
        modules: mapModules(outline.modules, (module) => ({
          ...module,
          items: module.items.map((item) =>
            item.id === id ? { ...item, ...pickOutlineFields(patch) } : item,
          ),
        })),
      })),
    onSuccess: (item) =>
      queryClient.setQueryData(queryKeys.item(item.id), (current: object | undefined) =>
        current ? { ...current, ...item } : current,
      ),
    onSettled: invalidate,
  });

  const moveItem = useMutation({
    mutationFn: ({ id, moduleId, position }: { id: string; moduleId: string; position: number }) =>
      coursesApi.moveItem(id, { moduleId, position }),
    onSettled: invalidate,
  });

  const duplicateItem = useMutation({
    mutationFn: (id: string) => coursesApi.duplicateItem(id),
    onSettled: invalidate,
  });

  const deleteItem = useMutation({
    mutationFn: (item: { id: string; title: string }) => coursesApi.deleteItem(item.id),
    onMutate: (target) =>
      patchOutline(queryClient, courseId, (outline) => ({
        ...outline,
        modules: mapModules(outline.modules, (module) => ({
          ...module,
          items: module.items.filter((item) => item.id !== target.id),
        })),
      })),
    onSuccess: (_result, item) =>
      toast({
        title: tUndo('itemDeleted', { title: item.title }),
        action: {
          label: tUndo('undo'),
          onClick: () => void coursesApi.restoreItem(item.id).then(invalidate),
        },
      }),
    onSettled: invalidate,
  });

  const restore = useMutation({
    mutationFn: ({ id, kind }: { id: string; kind: 'course' | 'module' | 'item' }) =>
      kind === 'item'
        ? coursesApi.restoreItem(id)
        : kind === 'module'
          ? coursesApi.restoreModule(id)
          : coursesApi.restore(id),
    onSettled: invalidate,
  });

  return {
    createModule,
    updateModule,
    moveModule,
    deleteModule,
    createItem,
    updateItem,
    moveItem,
    duplicateItem,
    deleteItem,
    restore,
  };
}

function stripUndefined<T extends object>(value: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(value).filter(([key, entry]) => entry !== undefined && key !== 'version'),
  ) as Partial<T>;
}

function pickOutlineFields(patch: ItemPatch): Partial<OutlineItem> {
  const { title, visibility, publishAt } = patch;
  return stripUndefined({ title, visibility, publishAt });
}
