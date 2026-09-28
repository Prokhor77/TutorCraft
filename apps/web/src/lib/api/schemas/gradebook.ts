import { z } from 'zod';
import { blockDocSchema } from './blockdoc';
import { idSchema, instantSchema } from './common';

export const AGGREGATIONS = ['weighted_mean', 'sum'] as const;
export const GRADEBOOK_WARNING_CODES = ['weights_not_100', 'empty_category', 'zero_max'] as const;

export const gradebookSetupSchema = z.object({
  aggregation: z.enum(AGGREGATIONS),
  categories: z.array(z.object({ id: idSchema, name: z.string(), weight: z.number() })),
  items: z.array(
    z.object({
      gradeItemId: idSchema,
      name: z.string(),
      maxScore: z.number(),
      categoryId: idSchema.nullable(),
      sourceItemId: idSchema.nullable(),
    }),
  ),
  scaleId: idSchema.nullable(),
  formula: z.string(),
  warnings: z.array(z.object({ code: z.string(), message: z.string() })),
});
export type GradebookSetup = z.infer<typeof gradebookSetupSchema>;

export const gradeCellSchema = z.object({
  gradeId: idSchema.nullable(),
  score: z.number().nullable(),
  overridden: z.boolean(),
  locked: z.boolean(),
  published: z.boolean(),
  version: z.number(),
});
export type GradeCell = z.infer<typeof gradeCellSchema>;

export const gradebookSchema = z.object({
  columns: z.array(
    z.object({
      gradeItemId: idSchema,
      name: z.string(),
      maxScore: z.number(),
      categoryId: idSchema.nullable(),
    }),
  ),
  rows: z.array(
    z.object({
      userId: idSchema,
      userName: z.string(),
      cells: z.record(gradeCellSchema),
      finalPercent: z.number().nullable(),
      finalLabel: z.string().nullable(),
    }),
  ),
});
export type Gradebook = z.infer<typeof gradebookSchema>;

export const gradeHistoryEntrySchema = z.object({
  at: instantSchema,
  actorName: z.string(),
  oldScore: z.number().nullable(),
  newScore: z.number().nullable(),
});
export type GradeHistoryEntry = z.infer<typeof gradeHistoryEntrySchema>;

export const myCourseGradesSchema = z.object({
  courseId: idSchema,
  items: z.array(
    z.object({
      gradeItemId: idSchema,
      name: z.string(),
      score: z.number().nullable(),
      maxScore: z.number(),
      feedback: blockDocSchema.nullable(),
    }),
  ),
  finalPercent: z.number().nullable(),
  finalLabel: z.string().nullable(),
});
export type MyCourseGrades = z.infer<typeof myCourseGradesSchema>;

/** Contract: GET /me/grades → MyGradesOverview (shape not specified). Assumed: per-course summary list. */
export const myGradesOverviewSchema = z.object({
  courses: z.array(
    z.object({
      courseId: idSchema,
      courseTitle: z.string(),
      finalPercent: z.number().nullable(),
      finalLabel: z.string().nullable(),
    }),
  ),
});
export type MyGradesOverview = z.infer<typeof myGradesOverviewSchema>;

export const scaleSchema = z.object({
  id: idSchema,
  name: z.string(),
  levels: z.array(z.object({ name: z.string(), minPercent: z.number() })),
});
export type Scale = z.infer<typeof scaleSchema>;
