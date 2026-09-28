import { z } from 'zod';
import { http } from '../http';
import {
  gradeCellSchema,
  gradeHistoryEntrySchema,
  gradebookSchema,
  gradebookSetupSchema,
  scaleSchema,
} from '../schemas/gradebook';

export type GradebookSetupInput = {
  aggregation: 'weighted_mean' | 'sum';
  categories: { id?: string; name: string; weight: number }[];
  items: { gradeItemId: string; categoryId: string | null }[];
  scaleId?: string | null;
};
export type ExportFormat = 'csv' | 'xlsx';

export const gradebookApi = {
  get: (courseId: string, groupId?: string) =>
    http.request(`/courses/${courseId}/gradebook`, { query: { groupId }, schema: gradebookSchema }),
  setup: (courseId: string) =>
    http.request(`/courses/${courseId}/gradebook/setup`, { schema: gradebookSetupSchema }),
  saveSetup: (courseId: string, body: GradebookSetupInput) =>
    http.request(`/courses/${courseId}/gradebook/setup`, {
      method: 'PUT',
      body,
      schema: gradebookSetupSchema,
    }),
  updateGrade: (
    gradeId: string,
    body: { score: number | null; locked?: boolean; version: number },
  ) =>
    http.request(`/grades/${gradeId}`, {
      method: 'PATCH',
      body,
      ifMatch: body.version,
      schema: gradeCellSchema.partial(),
    }),
  putCell: (
    courseId: string,
    body: { gradeItemId: string; userId: string; score: number | null },
  ) =>
    http.request(`/courses/${courseId}/gradebook/cells`, {
      method: 'PUT',
      body,
      schema: gradeCellSchema,
    }),
  createManualItem: (
    courseId: string,
    body: { name: string; maxScore: number; categoryId?: string | null },
  ) => http.request(`/courses/${courseId}/gradebook/manual-items`, { method: 'POST', body }),
  history: (gradeId: string) =>
    http.request(`/grades/${gradeId}/history`, { schema: z.array(gradeHistoryEntrySchema) }),
  export: (courseId: string, format: ExportFormat) =>
    http.requestBlob(`/courses/${courseId}/gradebook/export`, { query: { format } }),
  scales: () => http.request('/scales', { schema: z.array(scaleSchema) }),
  createScale: (body: { name: string; levels: { name: string; minPercent: number }[] }) =>
    http.request('/scales', { method: 'POST', body, schema: scaleSchema }),
  progressReport: (courseId: string, groupId?: string) =>
    http.request(`/courses/${courseId}/reports/progress`, {
      query: { groupId },
      schema: z.object({
        items: z.array(z.object({ id: z.string(), title: z.string() })),
        rows: z.array(
          z.object({
            userId: z.string(),
            userName: z.string(),
            completed: z.array(z.string()),
            percent: z.number(),
            completedAt: z.string().nullable(),
          }),
        ),
      }),
    }),
};
