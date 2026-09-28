import { z } from 'zod';
import { idSchema, instantSchema, nullableInstant, tenantRoleSchema } from './common';

/** How an account appeared (users.created_via, contract §4.1). */
export const ACCOUNT_ORIGINS = [
  'unknown',
  'self_signup',
  'school_owner',
  'tutor_invite',
  'admin',
  'import',
  'system',
] as const;
export const accountOriginSchema = z.enum(ACCOUNT_ORIGINS);
export type AccountOrigin = z.infer<typeof accountOriginSchema>;

/** List filter: school status or `blocked` (blocked by the platform administrator). */
export const MEMBER_STATUS_FILTERS = ['active', 'suspended', 'invited', 'blocked'] as const;
export type MemberStatusFilter = (typeof MEMBER_STATUS_FILTERS)[number];

const creatorSchema = z.object({
  id: idSchema,
  email: z.string(),
  firstName: z.string(),
  lastName: z.string(),
});
export type AccountCreator = z.infer<typeof creatorSchema>;

const memberBase = {
  id: idSchema,
  email: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  status: z.enum(['active', 'suspended', 'invited']),
  origin: accountOriginSchema,
  createdBy: creatorSchema.nullable(),
  tenantRoles: z.array(tenantRoleSchema),
  lastLoginAt: nullableInstant,
  createdAt: instantSchema,
};

export const schoolMemberSchema = z.object({ ...memberBase, platformBlocked: z.boolean() });
export type SchoolMember = z.infer<typeof schoolMemberSchema>;

export const platformUserSchema = z.object({
  ...memberBase,
  platformBlock: z.object({ at: instantSchema, reason: z.string().nullable() }).nullable(),
  school: z.object({ id: idSchema, slug: z.string(), name: z.string() }),
});
export type PlatformUser = z.infer<typeof platformUserSchema>;

export const courseInvitationResultSchema = z.object({
  userId: idSchema,
  accountCreated: z.boolean(),
  activationUrl: z.string().nullable(),
});
export type CourseInvitationResult = z.infer<typeof courseInvitationResultSchema>;

export const activationLinkSchema = z.object({ activationUrl: z.string() });
