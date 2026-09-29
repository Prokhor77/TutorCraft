'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  calendarApi,
  type LessonInput,
  type NoteInput,
  type NotePatch,
} from '@/lib/api/endpoints/calendar';
import { queryKeys } from '../query-keys';

/** Courses where the current user may schedule lessons (empty for students). */
export function useLessonCourses() {
  return useQuery({ queryKey: queryKeys.lessonCourses, queryFn: calendarApi.lessonCourses });
}

export function useLessonStudents(courseId: string) {
  return useQuery({
    queryKey: queryKeys.lessonStudents(courseId),
    queryFn: () => calendarApi.students(courseId),
    enabled: !!courseId,
  });
}

/** Every calendar mutation refreshes all loaded calendar ranges. */
function useCalendarMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.calendarRoot }),
  });
}

export function useCreateNote() {
  return useCalendarMutation((input: NoteInput) => calendarApi.createNote(input));
}

export function useUpdateNote() {
  return useCalendarMutation(({ id, patch }: { id: string; patch: NotePatch }) =>
    calendarApi.updateNote(id, patch),
  );
}

export function useCreateLesson() {
  return useCalendarMutation(({ courseId, input }: { courseId: string; input: LessonInput }) =>
    calendarApi.createLesson(courseId, input),
  );
}

export function useUpdateLesson() {
  return useCalendarMutation(
    (args: { courseId: string; lessonId: string; version: number; input: LessonInput }) =>
      calendarApi.updateLesson(args.courseId, args.lessonId, args.version, args.input),
  );
}

/** Deletes a note or a lesson depending on the event kind. */
export function useDeleteEvent() {
  return useCalendarMutation(({ id, courseId }: { id: string; courseId: string | null }) =>
    courseId ? calendarApi.deleteLesson(courseId, id) : calendarApi.deleteNote(id),
  );
}
