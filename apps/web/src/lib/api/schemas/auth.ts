import { z } from 'zod';
import { idSchema, tenantRoleSchema } from './common';

export const brandingSchema = z.object({
  logoUrl: z.string().nullable(),
  primaryColor: z.string().nullable(),
});
export type Branding = z.infer<typeof brandingSchema>;

export const LOCALES = ['ru', 'en', 'uz'] as const;
export const localeSchema = z.enum(LOCALES);
export type Locale = z.infer<typeof localeSchema>;

export const meSchema = z.object({
  id: idSchema,
  email: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  avatarUrl: z.string().nullable(),
  timezone: z.string(),
  locale: localeSchema,
  tenant: z.object({ id: idSchema, slug: z.string(), name: z.string(), branding: brandingSchema }),
  tenantRoles: z.array(tenantRoleSchema),
  telegramLinked: z.boolean(),
});
export type Me = z.infer<typeof meSchema>;

export const authResponseSchema = z.object({
  accessToken: z.string(),
  expiresIn: z.number(),
  user: meSchema,
});
export type AuthResponse = z.infer<typeof authResponseSchema>;

export const providersSchema = z.object({
  google: z.object({ clientId: z.string() }).nullable(),
  telegram: z.object({ botUsername: z.string() }).nullable(),
});
export type AuthProviders = z.infer<typeof providersSchema>;

export const tenantChoiceSchema = z.object({ slug: z.string(), name: z.string() });
export type TenantChoice = z.infer<typeof tenantChoiceSchema>;

export type TelegramAuthPayload = {
  id: number;
  first_name: string;
  last_name?: string;
  username?: string;
  photo_url?: string;
  auth_date: number;
  hash: string;
};
