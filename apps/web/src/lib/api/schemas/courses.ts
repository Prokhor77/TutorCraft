import { z } from 'zod';
import { blockDocSchema } from './blockdoc';
import {
  courseRoleSchema,
  idSchema,
  instantSchema,
  itemTypeSchema,
  nullableInstant,
  progressStatusSchema,
  visibilitySchema,
} from './common';

export const courseCardSchema = z.object({
  id: idSchema,
  title: z.string(),
  shortName: z.string().nullable(),
  coverUrl: z.string().nullable(),
  categoryId: idSchema.nullable(),
  role: courseRoleSchema.nullable(),
  progressPercent: z.number().nullable(),
  visibility: visibilitySchema,
});
export type CourseCard = z.infer<typeof courseCardSchema>;

export const GROUP_MODES = ['none', 'visible', 'separate'] as const;
export const courseCompletionRuleSchema = z.object({
  requiredItemIds: z.array(idSchema),
  minFinalPercent: z.number().nullable(),
});
export type CourseCompletionRule = z.infer<typeof courseCompletionRuleSchema>;

export const selfEnrolSchema = z.object({
  enabled: z.boolean(),
  code: z.string().nullable(),
  maxStudents: z.number().nullable(),
  until: nullableInstant,
});
export type SelfEnrol = z.infer<typeof selfEnrolSchema>;

export const courseSchema = z.object({
  id: idSchema,
  title: z.string(),
  shortName: z.string().nullable(),
  slug: z.string(),
  categoryId: idSchema.nullable(),
  description: blockDocSchema.nullable(),
  coverFileId: idSchema.nullable(),
  coverUrl: z.string().nullable(),
  startsAt: nullableInstant,
  endsAt: nullableInstant,
  visibility: visibilitySchema,
  publishAt: nullableInstant,
  selfEnrol: selfEnrolSchema,
  completionRule: courseCompletionRuleSchema,
  groupMode: z.enum(GROUP_MODES),
  myRole: courseRoleSchema.nullable(),
  permissions: z.array(z.string()),
  version: z.number(),
});
export type Course = z.infer<typeof courseSchema>;

export const availabilitySchema = z.object({
  available: z.boolean(),
  mode: z.enum(['show_locked', 'hide']),
  reasons: z.array(z.string()),
});
export type Availability = z.infer<typeof availabilitySchema>;

export const outlineItemSchema = z.object({
  id: idSchema,
  type: itemTypeSchema,
  title: z.string(),
  position: z.number(),
  visibility: visibilitySchema,
  publishAt: nullableInstant,
  dueAt: nullableInstant,
  availability: availabilitySchema,
  completion: z.enum(['complete', 'incomplete']).nullable(),
  status: progressStatusSchema.nullable(),
  version: z.number(),
});
export type OutlineItem = z.infer<typeof outlineItemSchema>;

export type OutlineModule = {
  id: string;
  parentId: string | null;
  title: string;
  position: number;
  visibility: z.infer<typeof visibilitySchema>;
  publishAt: string | null;
  availability: Availability;
  items: OutlineItem[];
  children: OutlineModule[];
  version: number;
};
export const outlineModuleSchema: z.ZodType<OutlineModule> = z.lazy(() =>
  z.object({
    id: idSchema,
    parentId: idSchema.nullable(),
    title: z.string(),
    position: z.number(),
    visibility: visibilitySchema,
    publishAt: nullableInstant,
    availability: availabilitySchema,
    items: z.array(outlineItemSchema),
    children: z.array(outlineModuleSchema),
    version: z.number(),
  }),
);
export const courseOutlineSchema = z.object({
  courseId: idSchema,
  modules: z.array(outlineModuleSchema),
});
export type CourseOutline = z.infer<typeof courseOutlineSchema>;

export const moduleSchema = z.object({
  id: idSchema,
  parentId: idSchema.nullable().optional(),
  title: z.string(),
  position: z.number().optional(),
  visibility: visibilitySchema.optional(),
  version: z.number().optional(),
});
export type CourseModule = z.infer<typeof moduleSchema>;

// ---- conditions (FR-PROG-02) ----
export const conditionSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal('date'),
    from: instantSchema.optional(),
    until: instantSchema.optional(),
  }),
  z.object({
    type: z.literal('completion'),
    itemId: idSchema,
    state: z.enum(['complete', 'incomplete']),
  }),
  z.object({
    type: z.literal('grade'),
    itemId: idSchema,
    minPercent: z.number().optional(),
    maxPercent: z.number().optional(),
  }),
  z.object({ type: z.literal('group'), groupId: idSchema }),
]);
export type Condition = z.infer<typeof conditionSchema>;
export const conditionGroupSchema = z.object({
  op: z.enum(['all', 'any']),
  showWhenLocked: z.boolean(),
  conditions: z.array(conditionSchema),
});
export type ConditionGroup = z.infer<typeof conditionGroupSchema>;

// ---- item settings (DATA-04) ----
export const REVIEW_TIMINGS = ['immediately', 'after_close', 'never'] as const;
const reviewTiming = z.enum(REVIEW_TIMINGS);
export const SUBMISSION_TYPES = ['file', 'text', 'both', 'none'] as const;
export const GRADING_METHODS = ['highest', 'last', 'average', 'first'] as const;
export const FORUM_TYPES = ['general', 'qa', 'announcements'] as const;
export const VIDEO_STATUSES = ['processing', 'ready', 'failed'] as const;

export const assignmentSettingsSchema = z.object({
  kind: z.literal('assignment'),
  submissionType: z.enum(SUBMISSION_TYPES),
  maxScore: z.number(),
  dueAt: nullableInstant,
  openAt: nullableInstant,
  closeAt: nullableInstant,
  allowedExtensions: z.array(z.string()),
  maxFiles: z.number(),
  maxFileSizeMb: z.number(),
  maxAttempts: z.number().nullable(),
  groupSubmission: z.boolean(),
  requireSubmitButton: z.boolean(),
  gradeCategoryId: idSchema.nullable(),
  autoPublishGrades: z.boolean(),
});
export type AssignmentSettings = z.infer<typeof assignmentSettingsSchema>;

export const quizSettingsSchema = z.object({
  kind: z.literal('quiz'),
  openAt: nullableInstant,
  closeAt: nullableInstant,
  timeLimitSec: z.number().nullable(),
  maxAttempts: z.number().nullable(),
  gradingMethod: z.enum(GRADING_METHODS),
  passPercent: z.number().nullable(),
  shuffleQuestions: z.boolean(),
  shuffleAnswers: z.boolean(),
  questionsPerPage: z.number(),
  maxScore: z.number(),
  review: z.object({
    whenScore: reviewTiming,
    whenCorrectness: reviewTiming,
    whenCorrectAnswers: reviewTiming,
    whenFeedback: reviewTiming,
  }),
  gradeCategoryId: idSchema.nullable(),
});
export type QuizSettings = z.infer<typeof quizSettingsSchema>;

export const forumSettingsSchema = z.object({
  kind: z.literal('forum'),
  forumType: z.enum(FORUM_TYPES),
  editWindowMinutes: z.number(),
  gradeCategoryId: idSchema.nullable(),
});
export type ForumSettings = z.infer<typeof forumSettingsSchema>;

export const itemSettingsSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('page') }),
  z.object({ kind: z.literal('file'), fileId: idSchema.nullable() }),
  z.object({ kind: z.literal('url'), url: z.string() }),
  z.object({ kind: z.literal('folder'), fileIds: z.array(idSchema) }),
  z.object({
    kind: z.literal('video'),
    fileId: idSchema.nullable(),
    embedUrl: z.string().nullable(),
    videoStatus: z.enum(VIDEO_STATUSES).optional(),
    hlsUrl: z.string().nullable().optional(),
  }),
  assignmentSettingsSchema,
  quizSettingsSchema,
  forumSettingsSchema,
]);
export type ItemSettings = z.infer<typeof itemSettingsSchema>;
export type SettingsOf<K extends ItemSettings['kind']> = Extract<ItemSettings, { kind: K }>;

export const COMPLETION_TRIGGERS = ['viewed', 'submitted', 'graded', 'passed', 'posted'] as const;
export const itemCompletionRuleSchema = z.object({
  mode: z.enum(['none', 'manual', 'auto']),
  on: z.array(z.enum(COMPLETION_TRIGGERS)).optional(),
});
export type ItemCompletionRule = z.infer<typeof itemCompletionRuleSchema>;

export const itemSchema = outlineItemSchema.extend({
  moduleId: idSchema,
  courseId: idSchema,
  settings: itemSettingsSchema,
  content: blockDocSchema.nullable(),
  completionRule: itemCompletionRuleSchema,
  conditions: conditionGroupSchema.nullable(),
});
export type Item = z.infer<typeof itemSchema>;

export const itemDetailSchema = itemSchema.extend({ permissions: z.array(z.string()) });
export type ItemDetail = z.infer<typeof itemDetailSchema>;

/** Contract names `TrashEntry` without a shape; assumed minimal shape (see README "API assumptions"). */
export const trashEntrySchema = z.object({
  id: idSchema,
  kind: z.enum(['course', 'module', 'item']),
  title: z.string(),
  itemType: itemTypeSchema.nullable().optional(),
  deletedAt: instantSchema,
});
export type TrashEntry = z.infer<typeof trashEntrySchema>;

export const publicCourseSchema = z.object({
  id: idSchema,
  slug: z.string(),
  title: z.string(),
  description: blockDocSchema.nullable(),
  coverUrl: z.string().nullable(),
  teacher: z.object({ name: z.string(), avatarUrl: z.string().nullable() }),
  modules: z.array(z.object({ title: z.string(), itemCount: z.number() })),
  selfEnrolEnabled: z.boolean(),
  tenantSlug: z.string(),
  tenantName: z.string(),
});
export type PublicCourse = z.infer<typeof publicCourseSchema>;

export const completionMeSchema = z.object({
  percent: z.number(),
  completedAt: nullableInstant,
  items: z.record(z.enum(['complete', 'incomplete'])),
});
export type CompletionMe = z.infer<typeof completionMeSchema>;
