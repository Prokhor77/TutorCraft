'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useRef } from 'react';
import { coursesApi, type ItemPatch } from '@/lib/api/endpoints/courses';
import type { ItemDetail } from '@/lib/api/schemas/courses';
import { queryKeys } from '../query-keys';

export function useItem(itemId: string) {
  return useQuery({
    queryKey: queryKeys.item(itemId),
    queryFn: () => coursesApi.getItem(itemId),
    enabled: !!itemId,
  });
}

/**
 * PATCH /items/{id} with If-Match. Keeps the latest known version in a ref so consecutive autosaves
 * chain correctly (each response bumps `version`, API-06).
 */
export function useItemPatcher(item: ItemDetail | undefined) {
  const queryClient = useQueryClient();
  const versionRef = useRef(item?.version ?? 0);
  if (item && item.version > versionRef.current) versionRef.current = item.version;

  const mutation = useMutation({
    mutationFn: (patch: Omit<ItemPatch, 'version'>) =>
      coursesApi.updateItem(item?.id ?? '', { ...patch, version: versionRef.current }),
    onSuccess: (updated) => {
      versionRef.current = updated.version;
      queryClient.setQueryData<ItemDetail>(queryKeys.item(updated.id), (current) =>
        current ? { ...current, ...updated } : current,
      );
      void queryClient.invalidateQueries({ queryKey: queryKeys.outline(updated.courseId) });
    },
    onError: () => {
      if (item) void queryClient.invalidateQueries({ queryKey: queryKeys.item(item.id) });
    },
  });

  const patch = useCallback(
    (changes: Omit<ItemPatch, 'version'>) => mutation.mutateAsync(changes),
    [mutation],
  );
  return { patch, isSaving: mutation.isPending };
}

export function useManualCompletion(itemId: string, courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (complete: boolean) =>
      complete ? coursesApi.markComplete(itemId) : coursesApi.unmarkComplete(itemId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.outline(courseId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.completion(courseId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.myTasks });
    },
  });
}
