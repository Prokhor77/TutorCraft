import { z } from 'zod';
import { http } from '../http';
import { pageSchema, type CourseRole } from '../schemas/common';
import {
  acceptInviteLinkSchema,
  createdInviteLinkSchema,
  enrollmentSchema,
  groupSchema,
  inviteLinkSchema,
} from '../schemas/enrollment';

export type EnrollmentsQuery = {
  q?: string;
  role?: string;
  groupId?: string;
  cursor?: string | null;
};
export type EnrollmentPatch = {
  role?: CourseRole;
  status?: 'active' | 'suspended' | 'completed';
  startsAt?: string | null;
  endsAt?: string | null;
};
export type AutoGroupInput = { strategy: 'by_count' | 'by_size'; value: number; prefix?: string };

export const enrollmentApi = {
  list: (courseId: string, query: EnrollmentsQuery) =>
    http.request(`/courses/${courseId}/enrollments`, {
      query,
      schema: pageSchema(enrollmentSchema),
    }),
  enrol: (
    courseId: string,
    body: { userIds: string[]; role: CourseRole; startsAt?: string; endsAt?: string },
  ) =>
    http.request(`/courses/${courseId}/enrollments`, {
      method: 'POST',
      body,
      schema: z.object({ created: z.number() }),
    }),
  update: (id: string, body: EnrollmentPatch) =>
    http.request(`/enrollments/${id}`, { method: 'PATCH', body }),
  remove: (id: string) => http.request(`/enrollments/${id}`, { method: 'DELETE' }),
  selfEnrol: (courseId: string, code?: string) =>
    http.request(`/courses/${courseId}/self-enrol`, {
      method: 'POST',
      body: { code },
      schema: enrollmentSchema,
    }),
  createInviteLink: (
    courseId: string,
    body: { role: CourseRole; expiresAt?: string; maxUses?: number },
  ) =>
    http.request(`/courses/${courseId}/invite-links`, {
      method: 'POST',
      body,
      schema: createdInviteLinkSchema,
    }),
  inviteLinks: (courseId: string) =>
    http.request(`/courses/${courseId}/invite-links`, { schema: z.array(inviteLinkSchema) }),
  revokeInviteLink: (id: string) => http.request(`/invite-links/${id}`, { method: 'DELETE' }),
  acceptInviteLink: (token: string) =>
    http.request('/invite-links/accept', {
      method: 'POST',
      body: { token },
      schema: acceptInviteLinkSchema,
    }),
  groups: (courseId: string) =>
    http.request(`/courses/${courseId}/groups`, { schema: z.array(groupSchema) }),
  createGroup: (courseId: string, name: string) =>
    http.request(`/courses/${courseId}/groups`, {
      method: 'POST',
      body: { name },
      schema: groupSchema,
    }),
  updateGroup: (id: string, body: { name: string }) =>
    http.request(`/groups/${id}`, { method: 'PATCH', body }),
  deleteGroup: (id: string) => http.request(`/groups/${id}`, { method: 'DELETE' }),
  setGroupMembers: (id: string, userIds: string[]) =>
    http.request(`/groups/${id}/members`, { method: 'PUT', body: { userIds } }),
  autoGroups: (courseId: string, body: AutoGroupInput) =>
    http.request(`/courses/${courseId}/groups/auto`, {
      method: 'POST',
      body,
      schema: z.array(groupSchema),
    }),
};
