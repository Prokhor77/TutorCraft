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

export function useActivityTrail(entryId: string | null) {
  return useQuery({
    queryKey: queryKeys.activityTrail(entryId ?? ''),
    queryFn: () => activityApi.trail(entryId ?? ''),
    enabled: entryId !== null,
  });
}

export function useActivitySummary(includeAnonymous: boolean) {
  return useQuery({
    queryKey: queryKeys.activitySummary({ includeAnonymous }),
    queryFn: () => activityApi.summary({ includeAnonymous }),
  });
}
