'use client';
import { createContext, useContext, type ReactNode } from 'react';
import { can, PERMISSIONS, type Permission } from '@/lib/access/permissions';
import type { Course } from '@/lib/api/schemas/courses';
import { useUiStore } from '@/stores/ui-store';

type CourseContextValue = {
  course: Course;
  can: (permission: Permission) => boolean;
  editMode: boolean;
};
const CourseContext = createContext<CourseContextValue | null>(null);

/** Course-scoped permissions from Course.permissions (never role names, FR-ACL-02). */
export function CourseProvider({ course, children }: { course: Course; children: ReactNode }) {
  const viewAsStudent = useUiStore((state) => state.viewAsStudent);
  const check = (permission: Permission) => can(course.permissions, permission);
  const editMode = check(PERMISSIONS.courseEdit) && !viewAsStudent;
  return (
    <CourseContext.Provider value={{ course, can: check, editMode }}>
      {children}
    </CourseContext.Provider>
  );
}

export function useCourseContext(): CourseContextValue {
  const value = useContext(CourseContext);
  if (!value) throw new Error('useCourseContext must be used inside CourseProvider');
  return value;
}
