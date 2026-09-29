import { http } from '../http';
import { subscriptionSchema, type SubscriptionTerm } from '../schemas/billing';

/** School subscription to the platform (contract §13) — the only payment on the platform. */
export const billingApi = {
  subscription: () => http.request('/billing/subscription', { schema: subscriptionSchema }),
  purchaseSubscription: (term: SubscriptionTerm, idempotencyKey: string) =>
    http.request('/billing/subscription/purchases', {
      method: 'POST',
      body: { term },
      idempotencyKey,
      schema: subscriptionSchema,
    }),
};
