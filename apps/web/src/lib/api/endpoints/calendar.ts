import { z } from 'zod';
import { http } from '../http';
import { calendarEventSchema, lessonCourseSchema, lessonStudentSchema } from '../schemas/me';

/** Personal note (kind `personal`): visible only to its author. */
export type NoteInput = {
  title: string;
  description?: string | null;
  startsAt: string;
  endsAt?: string | null;
  allDay: boolean;
};
export type NotePatch = Partial<NoteInput> & { clearEnd?: boolean; clearDescription?: boolean };

/** Lesson of a course; empty `attendeeIds` = the whole course. */
export type LessonInput = {
  title: string;
  description?: string | null;
  startsAt: string;
  endsAt?: string | null;
  moduleId?: string | null;
  itemId?: string | null;
  attendeeIds: string[];
};

const lessonsPath = (courseId: string) => `/courses/${courseId}/calendar/lessons`;

export const calendarApi = {
  createNote: (body: NoteInput) =>
    http.request('/me/calendar/events', { method: 'POST', body, schema: calendarEventSchema }),
  updateNote: (id: string, body: NotePatch) =>
    http.request(`/me/calendar/events/${id}`, {
      method: 'PATCH',
      body,
      schema: calendarEventSchema,
    }),
  deleteNote: (id: string) => http.request(`/me/calendar/events/${id}`, { method: 'DELETE' }),

  lessonCourses: () =>
    http.request('/me/calendar/lesson-courses', { schema: z.array(lessonCourseSchema) }),
  students: (courseId: string) =>
    http.request(`/courses/${courseId}/calendar/students`, {
      schema: z.array(lessonStudentSchema),
    }),
  createLesson: (courseId: string, body: LessonInput) =>
    http.request(lessonsPath(courseId), { method: 'POST', body, schema: calendarEventSchema }),
  updateLesson: (courseId: string, lessonId: string, version: number, body: LessonInput) =>
    http.request(`${lessonsPath(courseId)}/${lessonId}`, {
      method: 'PUT',
      body: { ...body, version },
      ifMatch: version,
      schema: calendarEventSchema,
    }),
  deleteLesson: (courseId: string, lessonId: string) =>
    http.request(`${lessonsPath(courseId)}/${lessonId}`, { method: 'DELETE' }),
};
