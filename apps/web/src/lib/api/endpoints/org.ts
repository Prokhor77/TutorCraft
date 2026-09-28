import { z } from 'zod';
import { http } from '../http';
import { pageSchema, type TenantRole } from '../schemas/common';
import {
  auditEntrySchema,
  categorySchema,
  importCommitResultSchema,
  importPreviewSchema,
  tenantSettingsSchema,
  userSummarySchema,
  type TenantSettings,
} from '../schemas/org';

export type TenantPatch = Partial<{
  name: string;
  logoFileId: string | null;
  primaryColor: string | null;
  defaultLocale: 'ru' | 'en';
  defaultTimezone: string;
  passwordPolicy: TenantSettings['passwordPolicy'];
  embedWhitelist: string[];
}> & { version: number };

export type UsersQuery = { q?: string; status?: string; role?: string; cursor?: string | null };
export type CreateUserInput = {
  email: string;
  firstName: string;
  lastName: string;
  tenantRoles?: TenantRole[];
  sendInvite: boolean;
};
export type UserPatch = {
  firstName?: string;
  lastName?: string;
  status?: 'active' | 'suspended';
  tenantRoles?: TenantRole[];
};
export type AuditQuery = {
  actorId?: string;
  objectType?: string;
  from?: string;
  to?: string;
  cursor?: string | null;
};

export const orgApi = {
  tenant: () => http.request('/tenant', { schema: tenantSettingsSchema }),
  updateTenant: (body: TenantPatch) =>
    http.request('/tenant', {
      method: 'PATCH',
      body,
      ifMatch: body.version,
      schema: tenantSettingsSchema,
    }),
  categories: () => http.request('/categories', { schema: z.array(categorySchema) }),
  createCategory: (body: { name: string; parentId?: string | null }) =>
    http.request('/categories', { method: 'POST', body, schema: categorySchema }),
  updateCategory: (
    id: string,
    body: { name?: string; parentId?: string | null; moveToParent?: boolean; position?: number },
  ) => http.request(`/categories/${id}`, { method: 'PATCH', body }),
  deleteCategory: (id: string) => http.request(`/categories/${id}`, { method: 'DELETE' }),
  users: (query: UsersQuery) =>
    http.request('/users', { query, schema: pageSchema(userSummarySchema) }),
  createUser: (body: CreateUserInput) =>
    http.request('/users', { method: 'POST', body, schema: userSummarySchema }),
  updateUser: (id: string, body: UserPatch) =>
    http.request(`/users/${id}`, { method: 'PATCH', body, schema: userSummarySchema }),
  resendInvite: (id: string) => http.request(`/users/${id}/invite`, { method: 'POST' }),
  importPreview: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return http.request('/users/import/preview', {
      method: 'POST',
      body: form,
      schema: importPreviewSchema,
    });
  },
  importCommit: (previewId: string) =>
    http.request('/users/import/commit', {
      method: 'POST',
      body: { previewId },
      schema: importCommitResultSchema,
    }),
  auditLog: (query: AuditQuery) =>
    http.request('/audit-log', { query, schema: pageSchema(auditEntrySchema) }),
};
