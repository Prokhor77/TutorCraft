import type { TenantRole } from '@/lib/api/schemas/common';

/** Permission keys from docs/permissions.md (FR-ACL-02). UI checks permissions, never role names. */
export const PERMISSIONS = {
  tenantManage: 'tenant.manage',
  tenantBranding: 'tenant.branding',
  userView: 'user.view',
  userManage: 'user.manage',
  userImport: 'user.import',
  categoryManage: 'category.manage',
  courseCreate: 'course.create',
  courseView: 'course.view',
  courseViewHidden: 'course.viewHidden',
  courseEdit: 'course.edit',
  courseDelete: 'course.delete',
  coursePublish: 'course.publish',
  contentView: 'content.view',
  enrollmentView: 'enrollment.view',
  enrollmentManage: 'enrollment.manage',
  groupManage: 'group.manage',
  submissionSubmit: 'submission.submit',
  submissionViewAll: 'submission.viewAll',
  submissionGrade: 'submission.grade',
  gradeViewOwn: 'grade.viewOwn',
  gradeViewAll: 'grade.viewAll',
  gradeEdit: 'grade.edit',
  gradePublish: 'grade.publish',
  gradeExport: 'grade.export',
  gradebookConfigure: 'gradebook.configure',
  quizAttempt: 'quiz.attempt',
  quizManage: 'quiz.manage',
  quizViewReports: 'quiz.viewReports',
  qbankManage: 'qbank.manage',
  forumPost: 'forum.post',
  forumModerate: 'forum.moderate',
  forumAnnounce: 'forum.announce',
  completionViewAll: 'completion.viewAll',
  reportView: 'report.view',
  auditView: 'audit.view',
  integrationManage: 'integration.manage',
  billingManage: 'billing.manage',
  fileUpload: 'file.upload',
} as const;
export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];

export function can(
  permissions: readonly string[] | undefined | null,
  permission: Permission,
): boolean {
  return !!permissions?.includes(permission);
}

export function canAny(
  permissions: readonly string[] | undefined | null,
  required: readonly Permission[],
): boolean {
  return required.some((permission) => can(permissions, permission));
}

const ADMIN_ROLES: readonly TenantRole[] = ['platform_admin', 'tenant_admin'];

/**
 * Tenant-level UI hint only (nav visibility). The contract exposes tenant roles, not tenant permissions;
 * every admin endpoint is authorized server-side (FR-ACL-02).
 */
export function isTenantAdminHint(tenantRoles: readonly TenantRole[] | undefined): boolean {
  return !!tenantRoles?.some((role) => ADMIN_ROLES.includes(role));
}

export function canCreateCoursesHint(tenantRoles: readonly TenantRole[] | undefined): boolean {
  return !!tenantRoles?.length;
}
