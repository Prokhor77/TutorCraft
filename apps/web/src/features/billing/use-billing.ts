'use client';
import { useMutation, useQuery } from '@tanstack/react-query';
import { billingApi } from '@/lib/api/endpoints/billing';
import { enrollmentApi } from '@/lib/api/endpoints/enrollment';
import { newIdempotencyKey } from '@/lib/api/idempotency';
import { MS_PER_SECOND } from '@/lib/utils/time';
import { queryKeys } from '../query-keys';
import { ROUTES } from '../auth/routes';

const ORDER_KEY_PREFIX = 'tc:order-key:';
const ORDER_POLL_MS = 2 * MS_PER_SECOND;

/** One idempotency key per course purchase intent (API-05): double clicks/reloads never create two orders. */
function orderIdempotencyKey(courseId: string): string {
  const storageKey = ORDER_KEY_PREFIX + courseId;
  const existing = sessionStorage.getItem(storageKey);
  if (existing) return existing;
  const key = newIdempotencyKey();
  sessionStorage.setItem(storageKey, key);
  return key;
}

export function clearOrderIntent(courseId: string): void {
  sessionStorage.removeItem(ORDER_KEY_PREFIX + courseId);
}

export function useCreateOrder() {
  return useMutation({
    mutationFn: (courseId: string) => {
      const returnUrl = `${window.location.origin}${ROUTES.checkoutReturn}?courseId=${encodeURIComponent(courseId)}`;
      return billingApi.createOrder(courseId, returnUrl, orderIdempotencyKey(courseId));
    },
  });
}

export function useSelfEnrol() {
  return useMutation({
    mutationFn: ({ courseId, code }: { courseId: string; code?: string }) =>
      enrollmentApi.selfEnrol(courseId, code),
  });
}

const TERMINAL_STATUSES = new Set(['paid', 'failed', 'refunded', 'canceled']);

export function useOrder(orderId: string | null, poll = false) {
  return useQuery({
    queryKey: queryKeys.order(orderId ?? 'none'),
    queryFn: () => billingApi.order(orderId as string),
    enabled: !!orderId,
    refetchInterval: (query) =>
      poll && !TERMINAL_STATUSES.has(query.state.data?.status ?? '') ? ORDER_POLL_MS : false,
  });
}

export function useFakePay() {
  return useMutation({ mutationFn: (orderId: string) => billingApi.fakePay(orderId) });
}
