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
  /** Null for teacher «upcoming deadlines» (no per-student progress there). */
  status: progressStatusSchema.nullable(),
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

export const CALENDAR_EVENT_KINDS = ['due', 'open', 'close', 'lesson', 'personal'] as const;
export const LESSON_AUDIENCES = ['course', 'students'] as const;
/** Detail fields are optional on the wire: older core-api builds return only the base contract. */
export const calendarEventSchema = z.object({
  id: idSchema,
  title: z.string(),
  startsAt: instantSchema,
  endsAt: nullableInstant,
  courseId: idSchema.nullable(),
  itemId: idSchema.nullable(),
  kind: z.enum(CALENDAR_EVENT_KINDS),
  description: z.string().nullish(),
  allDay: z.boolean().optional().default(false),
  courseTitle: z.string().nullish(),
  moduleId: idSchema.nullish(),
  moduleTitle: z.string().nullish(),
  itemTitle: z.string().nullish(),
  audience: z.enum(LESSON_AUDIENCES).nullish(),
  attendeeIds: z.array(idSchema).optional().default([]),
  canEdit: z.boolean().optional().default(false),
  version: z.number().nullish(),
});
export type CalendarEvent = z.infer<typeof calendarEventSchema>;
export type LessonAudience = (typeof LESSON_AUDIENCES)[number];

export const lessonCourseSchema = z.object({ id: idSchema, title: z.string() });
export type LessonCourse = z.infer<typeof lessonCourseSchema>;

export const lessonStudentSchema = z.object({
  id: idSchema,
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
});
export type LessonStudent = z.infer<typeof lessonStudentSchema>;
