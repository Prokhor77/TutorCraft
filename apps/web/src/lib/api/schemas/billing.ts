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
