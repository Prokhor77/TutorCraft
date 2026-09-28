import { z } from 'zod';
import { http } from '../http';
import { pageSchema } from '../schemas/common';
import {
  apiTokenSummarySchema,
  createdTokenSchema,
  createdWebhookSchema,
  webhookDeliverySchema,
  webhookSchema,
} from '../schemas/integrations';

export const integrationsApi = {
  tokens: () => http.request('/tokens', { schema: z.array(apiTokenSummarySchema) }),
  createToken: (body: { name: string; scopes: string[]; expiresAt?: string }) =>
    http.request('/tokens', { method: 'POST', body, schema: createdTokenSchema }),
  revokeToken: (id: string) => http.request(`/tokens/${id}`, { method: 'DELETE' }),
  webhooks: () => http.request('/webhooks', { schema: z.array(webhookSchema) }),
  createWebhook: (body: { url: string; events: string[] }) =>
    http.request('/webhooks', { method: 'POST', body, schema: createdWebhookSchema }),
  deleteWebhook: (id: string) => http.request(`/webhooks/${id}`, { method: 'DELETE' }),
  deliveries: (id: string, cursor?: string | null) =>
    http.request(`/webhooks/${id}/deliveries`, {
      query: { cursor },
      schema: pageSchema(webhookDeliverySchema),
    }),
};
