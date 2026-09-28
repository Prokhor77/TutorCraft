import { z } from 'zod';
import { idSchema, instantSchema, moneySchema, nullableInstant } from './common';

export const ORDER_STATUSES = ['pending', 'paid', 'failed', 'refunded', 'canceled'] as const;
export const orderSchema = z.object({
  id: idSchema,
  courseId: idSchema,
  courseTitle: z.string(),
  buyerId: idSchema,
  buyerName: z.string(),
  amount: moneySchema,
  status: z.enum(ORDER_STATUSES),
  provider: z.string(),
  createdAt: instantSchema,
  paidAt: nullableInstant,
});
export type Order = z.infer<typeof orderSchema>;

export const createOrderResultSchema = z.object({
  orderId: idSchema,
  status: z.enum(ORDER_STATUSES),
  confirmationUrl: z.string().nullable(),
});
export type CreateOrderResult = z.infer<typeof createOrderResultSchema>;

/** Platform subscription of the school (contract §13.2): every term unlocks the same features. */
export const SUBSCRIPTION_TERMS = ['month', 'quarter', 'year'] as const;
export type SubscriptionTerm = (typeof SUBSCRIPTION_TERMS)[number];
export const SUBSCRIPTION_STATUSES = ['trial', 'active', 'expired'] as const;
export type SubscriptionStatus = (typeof SUBSCRIPTION_STATUSES)[number];

export const subscriptionSchema = z.object({
  status: z.enum(SUBSCRIPTION_STATUSES),
  trialEndsAt: instantSchema,
  paidUntil: nullableInstant,
  accessUntil: instantSchema,
  canManage: z.boolean(),
  terms: z.array(
    z.object({ term: z.enum(SUBSCRIPTION_TERMS), months: z.number().int(), price: moneySchema }),
  ),
  payments: z.array(
    z.object({
      id: idSchema,
      term: z.enum(SUBSCRIPTION_TERMS),
      amount: moneySchema,
      periodStart: instantSchema,
      periodEnd: instantSchema,
      createdAt: instantSchema,
    }),
  ),
});
export type Subscription = z.infer<typeof subscriptionSchema>;
