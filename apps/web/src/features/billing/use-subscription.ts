'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { billingApi } from '@/lib/api/endpoints/billing';
import { newIdempotencyKey } from '@/lib/api/idempotency';
import type { SubscriptionTerm } from '@/lib/api/schemas/billing';
import { queryKeys } from '../query-keys';

/** School subscription (contract §13.2). `enabled` — only for school staff; students get 403. */
export function useSubscription(enabled = true) {
  return useQuery({
    queryKey: queryKeys.subscription,
    queryFn: () => billingApi.subscription(),
    enabled,
  });
}

/** Buys a term; the fake provider activates it at once. One idempotency key per click (API-05). */
export function usePurchaseSubscription() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (term: SubscriptionTerm) =>
      billingApi.purchaseSubscription(term, newIdempotencyKey()),
    onSuccess: (subscription) => queryClient.setQueryData(queryKeys.subscription, subscription),
  });
}
