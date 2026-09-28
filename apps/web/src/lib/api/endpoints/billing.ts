import { http } from '../http';
import { pageSchema } from '../schemas/common';
import {
  createOrderResultSchema,
  orderSchema,
  subscriptionSchema,
  type SubscriptionTerm,
} from '../schemas/billing';

export const billingApi = {
  createOrder: (courseId: string, returnUrl: string, idempotencyKey: string) =>
    http.request(`/courses/${courseId}/orders`, {
      method: 'POST',
      body: { returnUrl },
      idempotencyKey,
      schema: createOrderResultSchema,
    }),
  order: (id: string) => http.request(`/orders/${id}`, { schema: orderSchema }),
  orders: (query: { courseId?: string; cursor?: string | null }) =>
    http.request('/billing/orders', { query, schema: pageSchema(orderSchema) }),
  fakePay: (orderId: string) => http.request(`/billing/fake/${orderId}/pay`, { method: 'POST' }),
  subscription: () => http.request('/billing/subscription', { schema: subscriptionSchema }),
  purchaseSubscription: (term: SubscriptionTerm, idempotencyKey: string) =>
    http.request('/billing/subscription/purchases', {
      method: 'POST',
      body: { term },
      idempotencyKey,
      schema: subscriptionSchema,
    }),
};
