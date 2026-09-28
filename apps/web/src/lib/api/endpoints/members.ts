import { http } from '../http';
import { pageSchema, type CourseRole } from '../schemas/common';
import {
  activationLinkSchema,
  courseInvitationResultSchema,
  platformUserSchema,
  schoolMemberSchema,
} from '../schemas/members';
import { userSummarySchema } from '../schemas/org';

export type MembersQuery = {
  q?: string;
  status?: string;
  origin?: string;
  cursor?: string | null;
};
export type PlatformUsersQuery = MembersQuery & { tenantId?: string };
export type CourseInvitationInput = {
  email: string;
  firstName: string;
  lastName: string;
  role: CourseRole;
};

/** Course teachers invite people themselves (`enrollment.manage`, contract §6.1). */
export const courseInvitationsApi = {
  invite: (courseId: string, body: CourseInvitationInput) =>
    http.request(`/courses/${courseId}/invitations`, {
      method: 'POST',
      body,
      schema: courseInvitationResultSchema,
    }),
  candidates: (courseId: string, query: { q?: string; cursor?: string | null }) =>
    http.request(`/courses/${courseId}/enrollment-candidates`, {
      query,
      schema: pageSchema(userSummarySchema),
    }),
};

/** The school owner's own students (`member.view` / `member.manage`, contract §4.1). */
export const schoolMembersApi = {
  list: (query: MembersQuery) =>
    http.request('/school/members', { query, schema: pageSchema(schoolMemberSchema) }),
  setStatus: (id: string, status: 'active' | 'suspended') =>
    http.request(`/school/members/${id}`, {
      method: 'PATCH',
      body: { status },
      schema: schoolMemberSchema,
    }),
  activationLink: (id: string) =>
    http.request(`/school/members/${id}/activation-link`, {
      method: 'POST',
      schema: activationLinkSchema,
    }),
};

/** Users of every school for the platform administrator (`platform.manage`, contract §4.2). */
export const platformUsersApi = {
  list: (query: PlatformUsersQuery) =>
    http.request('/platform/users', { query, schema: pageSchema(platformUserSchema) }),
  block: (id: string, reason?: string) =>
    http.request(`/platform/users/${id}/block`, {
      method: 'POST',
      body: { reason: reason || undefined },
      schema: platformUserSchema,
    }),
  unblock: (id: string) =>
    http.request(`/platform/users/${id}/unblock`, { method: 'POST', schema: platformUserSchema }),
  erase: (id: string) => http.request(`/platform/users/${id}`, { method: 'DELETE' }),
};
