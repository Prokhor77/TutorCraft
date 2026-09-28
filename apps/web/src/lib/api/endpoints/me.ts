import { z } from 'zod';
import { http } from '../http';
import { meSchema, type Locale } from '../schemas/auth';
import { pageSchema } from '../schemas/common';
import {
  myCourseGradesSchema,
  myGradesOverviewSchema,
  type MyGradesOverview,
} from '../schemas/gradebook';
import { courseCardSchema, completionMeSchema } from '../schemas/courses';
import {
  calendarEventSchema,
  myTasksSchema,
  notificationPreferencesSchema,
  notificationSchema,
  teacherHomeSchema,
  type NotificationPreferences,
} from '../schemas/me';

export const UNREAD_COUNT_HEADER = 'X-Unread-Count';

export type UpdateMeInput = {
  firstName?: string;
  lastName?: string;
  timezone?: string;
  locale?: Locale;
  avatarFileId?: string;
};

/** MyGradesOverview is unspecified in the contract: accept `{ courses: [...] }` or a bare array. */
const gradesOverviewInput = z.preprocess(
  (raw) => (Array.isArray(raw) ? { courses: raw } : raw),
  myGradesOverviewSchema,
) as z.ZodType<MyGradesOverview>;

export const meApi = {
  get: () => http.request('/me', { schema: meSchema }),
  update: (body: UpdateMeInput) => http.request('/me', { method: 'PATCH', body, schema: meSchema }),
  changePassword: (body: { currentPassword?: string; newPassword: string }) =>
    http.request('/me/password', { method: 'POST', body }),
  linkTelegram: () =>
    http.request('/me/telegram/link', {
      method: 'POST',
      schema: z.object({ deepLink: z.string() }),
    }),
  tasks: () => http.request('/me/tasks', { schema: myTasksSchema }),
  teaching: () => http.request('/me/teaching', { schema: teacherHomeSchema }),
  grades: () => http.request('/me/grades', { schema: gradesOverviewInput }),
  courseGrades: (courseId: string) =>
    http.request(`/me/grades/${courseId}`, { schema: myCourseGradesSchema }),
  courses: () => http.request('/me/courses', { schema: z.array(courseCardSchema) }),
  async notifications(cursor?: string | null) {
    const { data, headers } = await http.requestWithMeta('/me/notifications', {
      query: { cursor },
      schema: pageSchema(notificationSchema),
    });
    const unread = Number(headers.get(UNREAD_COUNT_HEADER) ?? 0);
    return { ...data, unreadCount: Number.isFinite(unread) ? unread : 0 };
  },
  markNotificationsRead: (body: { ids?: string[]; all?: boolean }) =>
    http.request('/me/notifications/read', { method: 'POST', body }),
  notificationPreferences: () =>
    http.request('/me/notification-preferences', {
      schema: notificationPreferencesSchema,
    }) as Promise<NotificationPreferences>,
  saveNotificationPreferences: (body: NotificationPreferences) =>
    http.request('/me/notification-preferences', {
      method: 'PUT',
      body,
      schema: notificationPreferencesSchema,
    }) as Promise<NotificationPreferences>,
  calendar: (from: string, to: string) =>
    http.request('/me/calendar', { query: { from, to }, schema: z.array(calendarEventSchema) }),
  icalToken: () =>
    http.request('/me/calendar/ical-token', {
      method: 'POST',
      schema: z.object({ url: z.string() }),
    }),
  courseCompletion: (courseId: string) =>
    http.request(`/courses/${courseId}/completion/me`, { schema: completionMeSchema }),
};
