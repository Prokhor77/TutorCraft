'use client';
import { useQuery } from '@tanstack/react-query';
import { meApi } from '@/lib/api/endpoints/me';
import type { CourseCard } from '@/lib/api/schemas/courses';
import { useAuthStore } from '@/stores/auth-store';
import { queryKeys } from '../query-keys';

const TEACHING_ROLES = new Set(['teacher', 'assistant']);
const LEARNING_ROLES = new Set(['student']);

export function useMyCourses() {
  const enabled = useAuthStore((state) => state.status === 'authenticated');
  return useQuery({ queryKey: queryKeys.myCourses, queryFn: meApi.courses, enabled });
}

/** UI hints for navigation and home layout only — authorization is server-side (FR-ACL-02). */
export function roleHints(courses: CourseCard[] | undefined): {
  teaches: boolean;
  learns: boolean;
} {
  const roles = new Set((courses ?? []).map((course) => course.role));
  return {
    teaches: [...roles].some((role) => role !== null && TEACHING_ROLES.has(role)),
    learns: [...roles].some((role) => role !== null && LEARNING_ROLES.has(role)),
  };
}

/** Number of courses where the user teaches (teacher home stat card). */
export function teachingCourseCount(courses: CourseCard[] | undefined): number {
  return (courses ?? []).filter((course) => course.role !== null && TEACHING_ROLES.has(course.role))
    .length;
}
