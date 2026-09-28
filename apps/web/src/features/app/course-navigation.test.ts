import { describe, expect, it } from 'vitest';
import { PERMISSIONS } from '@/lib/access/permissions';
import {
  activeCourseNavIndex,
  courseIdFromPath,
  courseMobileNav,
  courseTabs,
} from './course-navigation';

const TEACHER = [
  PERMISSIONS.courseEdit,
  PERMISSIONS.qbankManage,
  PERMISSIONS.gradeViewAll,
  PERMISSIONS.enrollmentView,
];
const STUDENT = [PERMISSIONS.gradeViewOwn];

describe('course navigation (Stitch header / bottom nav)', () => {
  it('extracts the course id from course routes only', () => {
    expect(courseIdFromPath('/courses/c1/items/i1')).toBe('c1');
    expect(courseIdFromPath('/courses/c1')).toBe('c1');
    expect(courseIdFromPath('/courses')).toBeNull();
    expect(courseIdFromPath('/dashboard')).toBeNull();
  });

  it('gives teachers the authoring tabs and learners their own set', () => {
    expect(courseTabs('c1', TEACHER).map((tab) => tab.labelKey)).toEqual([
      'builder',
      'quizBuilder',
      'media',
      'analytics',
      'participants',
    ]);
    expect(courseTabs('c1', STUDENT).map((tab) => tab.labelKey)).toEqual([
      'courseStudent',
      'media',
      'myGrades',
    ]);
  });

  it('highlights exactly one bottom-nav entry, preferring query-specific ones', () => {
    const items = courseMobileNav('c1', STUDENT);
    expect(items).toHaveLength(5);
    expect(activeCourseNavIndex(items, '/courses/c1', 'type=quiz')).toBe(1);
    expect(activeCourseNavIndex(items, '/courses/c1', '')).toBe(0);
    expect(activeCourseNavIndex(items, '/courses/c1/items/i1', '')).toBe(0);
    expect(activeCourseNavIndex(items, '/courses/c1/media', '')).toBe(3);
    expect(activeCourseNavIndex(courseMobileNav('c1', TEACHER), '/courses/c1/gradebook', '')).toBe(
      2,
    );
  });
});
