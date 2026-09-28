'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meApi } from '@/lib/api/endpoints/me';
import type { NotificationPreferences } from '@/lib/api/schemas/me';
import { getNextCursor } from '@/lib/api/pagination';
import { useAuthStore } from '@/stores/auth-store';
import { queryKeys } from '../query-keys';

export function useNotificationsFeed() {
  const enabled = useAuthStore((state) => state.status === 'authenticated');
  return useInfiniteQuery({
    queryKey: queryKeys.notifications,
    queryFn: ({ pageParam }) => meApi.notifications(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
    enabled,
  });
}

export function useMarkNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { ids?: string[]; all?: boolean }) => meApi.markNotificationsRead(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
  });
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: queryKeys.notificationPreferences,
    queryFn: meApi.notificationPreferences,
  });
}

export function useSaveNotificationPreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (prefs: NotificationPreferences) => meApi.saveNotificationPreferences(prefs),
    onSuccess: (prefs) => queryClient.setQueryData(queryKeys.notificationPreferences, prefs),
  });
}

export function useLinkTelegram() {
  return useMutation({ mutationFn: meApi.linkTelegram });
}
