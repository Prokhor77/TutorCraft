'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from '@/components/ui/toast';
import {
  coursesApi,
  type CoursePatch,
  type CoursesQuery,
  type CreateCourseInput,
} from '@/lib/api/endpoints/courses';
import { getNextCursor } from '@/lib/api/pagination';
import type { Money } from '@/lib/api/schemas/common';
import type { Course } from '@/lib/api/schemas/courses';
import { queryKeys } from '../query-keys';

export function useCourseList(params: Omit<CoursesQuery, 'cursor'>) {
  return useInfiniteQuery({
    queryKey: queryKeys.courseList(params),
    queryFn: ({ pageParam }) => coursesApi.list({ ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

export function useCourse(courseId: string) {
  return useQuery({
    queryKey: queryKeys.course(courseId),
    queryFn: () => coursesApi.get(courseId),
  });
}

export function useCreateCourse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateCourseInput) => coursesApi.create(input),
    onSuccess: (course) => {
      queryClient.setQueryData(queryKeys.course(course.id), course);
      void queryClient.invalidateQueries({ queryKey: queryKeys.courses });
      void queryClient.invalidateQueries({ queryKey: queryKeys.myCourses });
    },
  });
}

/** PATCH with If-Match (API-06); on 412 the course is refetched so the user sees the latest version. */
export function useUpdateCourse(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ version, patch }: { version: number; patch: CoursePatch }) =>
      coursesApi.update(courseId, version, patch),
    onSuccess: (course) => {
      queryClient.setQueryData(queryKeys.course(courseId), course);
      void queryClient.invalidateQueries({ queryKey: queryKeys.myCourses });
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: queryKeys.course(courseId) }),
  });
}

export function useSetCoursePrice(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (price: Money | null) => coursesApi.setPrice(courseId, price),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.course(courseId) }),
  });
}

export function useDuplicateCourse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (courseId: string) => coursesApi.duplicate(courseId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.courses }),
  });
}

/** UX-06: delete immediately, offer Undo in the toast (restore endpoint). */
export function useDeleteCourse() {
  const queryClient = useQueryClient();
  const t = useTranslations('undo');
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.courses });
    void queryClient.invalidateQueries({ queryKey: queryKeys.myCourses });
  };
  return useMutation({
    mutationFn: (course: Pick<Course, 'id' | 'title'>) => coursesApi.remove(course.id),
    onSuccess: (_result, course) => {
      invalidate();
      toast({
        title: t('courseDeleted', { title: course.title }),
        action: {
          label: t('undo'),
          onClick: () => void coursesApi.restore(course.id).then(invalidate),
        },
      });
    },
  });
}
