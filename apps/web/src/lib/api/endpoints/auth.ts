import { z } from 'zod';
import { http } from '../http';
import { authResponseSchema, providersSchema } from '../schemas/auth';

export type LoginInput = { email: string; password: string; tenantSlug?: string };
export type RegisterInput = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  schoolName?: string;
  /** Offer + personal data policy accepted (core-api rejects sign-up without it). */
  acceptTerms: true;
};
export type AcceptInvitationInput = {
  token: string;
  password: string;
  firstName: string;
  lastName: string;
  acceptTerms: true;
};

const PUBLIC = { auth: false } as const;

export const authApi = {
  providers: () => http.request('/auth/providers', { ...PUBLIC, schema: providersSchema }),
  register: (body: RegisterInput) =>
    http.request('/auth/register', { ...PUBLIC, method: 'POST', body, schema: authResponseSchema }),
  login: (body: LoginInput) =>
    http.request('/auth/login', { ...PUBLIC, method: 'POST', body, schema: authResponseSchema }),
  google: (body: { idToken: string; tenantSlug?: string }) =>
    http.request('/auth/oauth/google', {
      ...PUBLIC,
      method: 'POST',
      body,
      schema: authResponseSchema,
    }),
  logout: () => http.request('/auth/logout', { ...PUBLIC, method: 'POST' }),
  logoutAll: () => http.request('/auth/logout-all', { method: 'POST' }),
  forgotPassword: (body: { email: string; tenantSlug?: string }) =>
    http.request('/auth/password/forgot', { ...PUBLIC, method: 'POST', body }),
  resetPassword: (body: { token: string; newPassword: string }) =>
    http.request('/auth/password/reset', { ...PUBLIC, method: 'POST', body }),
  acceptInvitation: (body: AcceptInvitationInput) =>
    http.request('/auth/invitations/accept', {
      ...PUBLIC,
      method: 'POST',
      body,
      schema: authResponseSchema,
    }),
};

export const tenantChoicesSchema = z.array(z.object({ slug: z.string(), name: z.string() }));
