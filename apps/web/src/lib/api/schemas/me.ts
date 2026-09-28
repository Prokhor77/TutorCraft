import { z } from 'zod';
import {
  idSchema,
  instantSchema,
  itemTypeSchema,
  nullableInstant,
  progressStatusSchema,
} from './common';

export const taskEntrySchema = z.object({
  itemId: idSchema,
  courseId: idSchema,
  courseTitle: z.string(),
  itemTitle: z.string(),
  itemType: itemTypeSchema,
  dueAt: nullableInstant,
  status: progressStatusSchema,
});
export type TaskEntry = z.infer<typeof taskEntrySchema>;

export const myTasksSchema = z.object({
  overdue: z.array(taskEntrySchema),
  today: z.array(taskEntrySchema),
  thisWeek: z.array(taskEntrySchema),
  later: z.array(taskEntrySchema),
  recentlyGraded: z.array(
    z.object({
      itemId: idSchema,
      courseId: idSchema,
      itemTitle: z.string(),
      courseTitle: z.string(),
      score: z.number(),
      maxScore: z.number(),
      gradedAt: instantSchema,
    }),
  ),
  continueLearning: z.array(
    z.object({
      courseId: idSchema,
      courseTitle: z.string(),
      itemId: idSchema,
      itemTitle: z.string(),
      progressPercent: z.number(),
    }),
  ),
});
export type MyTasks = z.infer<typeof myTasksSchema>;

export const teacherHomeSchema = z.object({
  toGrade: z.array(z.object({ courseId: idSchema, courseTitle: z.string(), count: z.number() })),
  toGradeTotal: z.number(),
  upcomingDeadlines: z.array(taskEntrySchema),
  recentPosts: z.array(
    z.object({
      discussionId: idSchema,
      courseId: idSchema,
      title: z.string(),
      authorName: z.string(),
      createdAt: instantSchema,
    }),
  ),
});
export type TeacherHome = z.infer<typeof teacherHomeSchema>;

export const notificationSchema = z.object({
  id: idSchema,
  type: z.string(),
  title: z.string(),
  body: z.string(),
  link: z.string().nullable(),
  readAt: nullableInstant,
  createdAt: instantSchema,
});
export type AppNotification = z.infer<typeof notificationSchema>;

export const NOTIFICATION_CATEGORIES = [
  'new_item',
  'deadline',
  'grade_published',
  'forum_reply',
  'announcement',
  'submission_received',
  'sale',
  'video_ready',
] as const;
export type NotificationCategory = (typeof NOTIFICATION_CATEGORIES)[number];
export const NOTIFICATION_CHANNELS = ['web', 'email', 'telegram'] as const;
export type NotificationChannel = (typeof NOTIFICATION_CHANNELS)[number];

const channelMatrixSchema = z.record(z.enum(NOTIFICATION_CHANNELS), z.boolean());
export const notificationPreferencesSchema = z.object({
  matrix: z.record(z.enum(NOTIFICATION_CATEGORIES), channelMatrixSchema),
});
export type NotificationPreferences = {
  matrix: Record<NotificationCategory, Record<NotificationChannel, boolean>>;
};

export const CALENDAR_EVENT_KINDS = ['due', 'open', 'close', 'personal'] as const;
export const calendarEventSchema = z.object({
  id: idSchema,
  title: z.string(),
  startsAt: instantSchema,
  endsAt: nullableInstant,
  courseId: idSchema.nullable(),
  itemId: idSchema.nullable(),
  kind: z.enum(CALENDAR_EVENT_KINDS),
});
export type CalendarEvent = z.infer<typeof calendarEventSchema>;
