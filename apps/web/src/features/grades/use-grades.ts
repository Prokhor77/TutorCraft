'use client';
import { useQuery } from '@tanstack/react-query';
import { meApi } from '@/lib/api/endpoints/me';
import { queryKeys } from '../query-keys';

export function useMyGrades() {
  return useQuery({ queryKey: queryKeys.myGrades, queryFn: meApi.grades });
}

export function useMyCourseGrades(courseId: string) {
  return useQuery({
    queryKey: queryKeys.myCourseGrades(courseId),
    queryFn: () => meApi.courseGrades(courseId),
  });
}
