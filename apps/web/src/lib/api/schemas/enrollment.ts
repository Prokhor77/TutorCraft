import { z } from 'zod';
import { courseRoleSchema, idSchema, instantSchema, nullableInstant } from './common';

export const ENROLLMENT_STATUSES = ['active', 'suspended', 'completed'] as const;
export const ENROLLMENT_METHODS = ['manual', 'self', 'invite_link', 'payment', 'import'] as const;

export const enrollmentSchema = z.object({
  id: idSchema,
  user: z.object({
    id: idSchema,
    firstName: z.string(),
    lastName: z.string(),
    email: z.string(),
    avatarUrl: z.string().nullable(),
  }),
  role: courseRoleSchema,
  status: z.enum(ENROLLMENT_STATUSES),
  method: z.enum(ENROLLMENT_METHODS),
  startsAt: nullableInstant,
  endsAt: nullableInstant,
  groupIds: z.array(idSchema),
  lastAccessAt: nullableInstant,
});
export type Enrollment = z.infer<typeof enrollmentSchema>;

export const groupSchema = z.object({
  id: idSchema,
  name: z.string(),
  memberIds: z.array(idSchema),
});
export type Group = z.infer<typeof groupSchema>;

/** Contract lists `InviteLink[]` (without token) with no shape; assumed fields (README "API assumptions"). */
export const inviteLinkSchema = z.object({
  id: idSchema,
  role: courseRoleSchema,
  expiresAt: nullableInstant.optional(),
  maxUses: z.number().nullable().optional(),
  uses: z.number().optional(),
  createdAt: instantSchema.optional(),
});
export type InviteLink = z.infer<typeof inviteLinkSchema>;

export const createdInviteLinkSchema = z.object({ id: idSchema, url: z.string() });
export const acceptInviteLinkSchema = z.object({ courseId: idSchema });
