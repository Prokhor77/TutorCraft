export const ROUTES = {
  landing: '/',
  login: '/login',
  register: '/register',
  forgotPassword: '/forgot-password',
  resetPassword: '/reset-password',
  acceptInvite: '/accept-invite',
  home: '/home',
  courses: '/courses',
  course: (id: string) => `/courses/${id}`,
  courseParticipants: (id: string) => `/courses/${id}/participants`,
  courseGradebook: (id: string) => `/courses/${id}/gradebook`,
  courseQuestionBank: (id: string) => `/courses/${id}/question-bank`,
  courseMedia: (id: string) => `/courses/${id}/media`,
  courseMyGrades: (id: string) => `/courses/${id}/grades`,
  courseTests: (id: string) => `/courses/${id}?type=quiz`,
  courseTrash: (id: string) => `/courses/${id}/trash`,
  item: (courseId: string, itemId: string) => `/courses/${courseId}/items/${itemId}`,
  itemSettings: (courseId: string, itemId: string) =>
    `/courses/${courseId}/items/${itemId}?tab=settings`,
  attempt: (courseId: string, itemId: string, attemptId: string) =>
    `/courses/${courseId}/items/${itemId}/attempts/${attemptId}`,
  attemptResult: (courseId: string, itemId: string, attemptId: string) =>
    `/courses/${courseId}/items/${itemId}/attempts/${attemptId}/result`,
  discussion: (courseId: string, itemId: string, discussionId: string) =>
    `/courses/${courseId}/items/${itemId}/discussions/${discussionId}`,
  grading: '/grading',
  gradingReview: '/grading/review',
  grades: '/grades',
  courseGrades: (courseId: string) => `/grades/${courseId}`,
  calendar: '/calendar',
  notifications: '/notifications',
  profile: '/settings/profile',
  notificationSettings: '/settings/notifications',
  admin: '/admin',
  adminUsers: '/admin/users',
  adminCategories: '/admin/categories',
  adminBranding: '/admin/branding',
  adminAudit: '/admin/audit',
  adminOrders: '/admin/orders',
  adminIntegrations: '/admin/integrations',
  catalog: (tenantSlug: string) => `/c/${tenantSlug}`,
  courseLanding: (tenantSlug: string, courseSlug: string) => `/c/${tenantSlug}/${courseSlug}`,
  fakeCheckout: (orderId: string) => `/checkout/fake/${orderId}`,
  checkoutReturn: '/checkout/return',
} as const;

export const NEXT_PARAM = 'next';

/** Only same-origin relative paths are allowed as post-login redirects (open-redirect guard). */
export function safeNextPath(
  value: string | null | undefined,
  fallback: string = ROUTES.home,
): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\'))
    return fallback;
  return value;
}

export function loginUrlWithNext(path: string): string {
  return `${ROUTES.login}?${NEXT_PARAM}=${encodeURIComponent(path)}`;
}
