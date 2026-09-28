'use client';
import { useQuery } from '@tanstack/react-query';
import { meApi } from '@/lib/api/endpoints/me';
import { queryKeys } from '../query-keys';

export function useMyTasks(enabled = true) {
  return useQuery({ queryKey: queryKeys.myTasks, queryFn: meApi.tasks, enabled });
}

export function useTeacherHome(enabled = true) {
  return useQuery({ queryKey: queryKeys.teaching, queryFn: meApi.teaching, enabled });
}
