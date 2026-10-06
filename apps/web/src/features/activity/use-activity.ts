'use client';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { activityApi, type ActivityQuery } from '@/lib/api/endpoints/activity';
import { getNextCursor } from '@/lib/api/pagination';
import { queryKeys } from '../query-keys';

export function useActivityLog(params: Omit<ActivityQuery, 'cursor'>) {
  return useInfiniteQuery({
    queryKey: queryKeys.activityLog(params),
    queryFn: ({ pageParam }) => activityApi.log({ ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

export function useActivityTrail(entryId: string | null, allTenants = false) {
  return useQuery({
    queryKey: queryKeys.activityTrail(entryId ?? '', allTenants),
    queryFn: () => activityApi.trail(entryId ?? '', { allTenants: allTenants || undefined }),
    enabled: entryId !== null,
  });
}

export function useActivitySummary(includeAnonymous: boolean, allTenants = false) {
  return useQuery({
    queryKey: queryKeys.activitySummary({ includeAnonymous, allTenants }),
    queryFn: () =>
      activityApi.summary({
        includeAnonymous: includeAnonymous || undefined,
        allTenants: allTenants || undefined,
      }),
  });
}
