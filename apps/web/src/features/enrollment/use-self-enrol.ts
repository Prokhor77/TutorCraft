'use client';
import { useMutation } from '@tanstack/react-query';
import { enrollmentApi } from '@/lib/api/endpoints/enrollment';

/** Self-enrolment from the public course landing (FR-ENROL-02); courses are free for students. */
export function useSelfEnrol() {
  return useMutation({
    mutationFn: ({ courseId, code }: { courseId: string; code?: string }) =>
      enrollmentApi.selfEnrol(courseId, code),
  });
}
