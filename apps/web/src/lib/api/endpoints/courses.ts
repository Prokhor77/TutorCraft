import { z } from 'zod';
import { http } from '../http';
import type { BlockDoc } from '../schemas/blockdoc';
import { pageSchema, type ItemType, type Money, type Visibility } from '../schemas/common';
import {
  courseCardSchema,
  courseOutlineSchema,
  courseSchema,
  itemDetailSchema,
  itemSchema,
  moduleSchema,
  publicCourseSchema,
  trashEntrySchema,
  type ConditionGroup,
  type Course,
  type ItemCompletionRule,
  type ItemSettings,
} from '../schemas/courses';

export type CoursesQuery = {
  q?: string;
  categoryId?: string;
  cursor?: string | null;
  mine?: boolean;
};
export type CreateCourseInput = {
  title: string;
  shortName?: string;
  categoryId?: string;
  description?: BlockDoc;
  startsAt?: string;
  endsAt?: string;
  coverFileId?: string;
};
export type CoursePatch = Partial<
  Pick<
    Course,
    | 'title'
    | 'shortName'
    | 'categoryId'
    | 'description'
    | 'coverFileId'
    | 'startsAt'
    | 'endsAt'
    | 'visibility'
    | 'publishAt'
    | 'selfEnrol'
    | 'completionRule'
    | 'groupMode'
  >
>;
export type ModulePatch = {
  title?: string;
  visibility?: Visibility;
  publishAt?: string | null;
  conditions?: ConditionGroup | null;
  version: number;
};
export type CreateItemInput = { type: ItemType; title: string; settings?: Partial<ItemSettings> };
export type ItemPatch = {
  title?: string;
  visibility?: Visibility;
  publishAt?: string | null;
  settings?: ItemSettings;
  content?: BlockDoc;
  completionRule?: ItemCompletionRule;
  conditions?: ConditionGroup | null;
  version: number;
};

export const coursesApi = {
  list: (query: CoursesQuery) =>
    http.request('/courses', { query, schema: pageSchema(courseCardSchema) }),
  create: (body: CreateCourseInput) =>
    http.request('/courses', { method: 'POST', body, schema: courseSchema }),
  get: (id: string) => http.request(`/courses/${id}`, { schema: courseSchema }),
  update: (id: string, version: number, body: CoursePatch) =>
    http.request(`/courses/${id}`, {
      method: 'PATCH',
      body: { ...body, version },
      ifMatch: version,
      schema: courseSchema,
    }),
  remove: (id: string) => http.request(`/courses/${id}`, { method: 'DELETE' }),
  restore: (id: string) => http.request(`/courses/${id}/restore`, { method: 'POST' }),
  duplicate: (id: string) =>
    http.request(`/courses/${id}/duplicate`, { method: 'POST', schema: courseSchema }),
  setPrice: (id: string, price: Money | null) =>
    http.request(`/courses/${id}/price`, { method: 'PUT', body: { price } }),
  outline: (id: string) => http.request(`/courses/${id}/outline`, { schema: courseOutlineSchema }),
  trash: (courseId: string) =>
    http.request('/trash', { query: { courseId }, schema: z.array(trashEntrySchema) }),

  createModule: (courseId: string, body: { title: string; parentId?: string | null }) =>
    http.request(`/courses/${courseId}/modules`, { method: 'POST', body, schema: moduleSchema }),
  updateModule: (id: string, body: ModulePatch) =>
    http.request(`/modules/${id}`, { method: 'PATCH', body, ifMatch: body.version }),
  deleteModule: (id: string) => http.request(`/modules/${id}`, { method: 'DELETE' }),
  moveModule: (id: string, body: { position: number; parentId?: string | null }) =>
    http.request(`/modules/${id}/move`, { method: 'POST', body }),
  /** Contract has no module restore/duplicate endpoints; restore goes through trash by kind (assumption, see README). */
  restoreModule: (id: string) => http.request(`/modules/${id}/restore`, { method: 'POST' }),

  createItem: (moduleId: string, body: CreateItemInput) =>
    http.request(`/modules/${moduleId}/items`, { method: 'POST', body, schema: itemSchema }),
  getItem: (id: string) => http.request(`/items/${id}`, { schema: itemDetailSchema }),
  updateItem: (id: string, body: ItemPatch) =>
    http.request(`/items/${id}`, {
      method: 'PATCH',
      body,
      ifMatch: body.version,
      schema: itemSchema,
    }),
  deleteItem: (id: string) => http.request(`/items/${id}`, { method: 'DELETE' }),
  restoreItem: (id: string) => http.request(`/items/${id}/restore`, { method: 'POST' }),
  moveItem: (id: string, body: { moduleId: string; position: number }) =>
    http.request(`/items/${id}/move`, { method: 'POST', body }),
  duplicateItem: (id: string) =>
    http.request(`/items/${id}/duplicate`, { method: 'POST', schema: itemSchema }),
  markComplete: (id: string) => http.request(`/items/${id}/complete`, { method: 'POST' }),
  unmarkComplete: (id: string) => http.request(`/items/${id}/complete`, { method: 'DELETE' }),
};

/** Public storefront — also used server-side (SSR) via fetchPublic in lib/server. */
export const publicApi = {
  catalog: (tenantSlug: string) =>
    http.request(`/public/${tenantSlug}/courses`, {
      auth: false,
      schema: z.array(publicCourseSchema),
    }),
  course: (tenantSlug: string, courseSlug: string) =>
    http.request(`/public/${tenantSlug}/courses/${courseSlug}`, {
      auth: false,
      schema: publicCourseSchema,
    }),
};
