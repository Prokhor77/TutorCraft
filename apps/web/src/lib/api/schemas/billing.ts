import { z } from 'zod';
import { idSchema, instantSchema, moneySchema, nullableInstant } from './common';

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
