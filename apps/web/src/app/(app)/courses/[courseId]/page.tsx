import { Suspense } from 'react';
import { CourseOutlineView } from '@/components/course/outline/course-outline';

export default function CoursePage() {
  return (
    <Suspense>
      <CourseOutlineView />
    </Suspense>
  );
}
