'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { billingApi } from '@/lib/api/endpoints/billing';
import { integrationsApi } from '@/lib/api/endpoints/integrations';
import {
  orgApi,
  type AuditQuery,
  type CreateUserInput,
  type TenantPatch,
  type UserPatch,
  type UsersQuery,
} from '@/lib/api/endpoints/org';
import { getNextCursor } from '@/lib/api/pagination';
import { useAuthStore } from '@/stores/auth-store';
import { queryKeys } from '../query-keys';

export function useUsers(params: Omit<UsersQuery, 'cursor'>, enabled = true) {
  return useInfiniteQuery({
    queryKey: queryKeys.users(params),
    queryFn: ({ pageParam }) => orgApi.users({ ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
    enabled,
  });
}

export function useUserMutations() {
  const queryClient = useQueryClient();
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: queryKeys.usersRoot });
  return {
    create: useMutation({
      mutationFn: (input: CreateUserInput) => orgApi.createUser(input),
      onSuccess: invalidate,
    }),
    update: useMutation({
      mutationFn: ({ id, patch }: { id: string; patch: UserPatch }) => orgApi.updateUser(id, patch),
      onSuccess: invalidate,
    }),
    resendInvite: useMutation({ mutationFn: (id: string) => orgApi.resendInvite(id) }),
    importPreview: useMutation({ mutationFn: (file: File) => orgApi.importPreview(file) }),
    importCommit: useMutation({
      mutationFn: (previewId: string) => orgApi.importCommit(previewId),
      onSuccess: invalidate,
    }),
  };
}

export function useCategories() {
  return useQuery({ queryKey: queryKeys.categories, queryFn: orgApi.categories });
}

export function useCategoryMutations() {
  const queryClient = useQueryClient();
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: queryKeys.categories });
  return {
    create: useMutation({
      mutationFn: (input: { name: string; parentId?: string | null }) =>
        orgApi.createCategory(input),
      onSuccess: invalidate,
    }),
    update: useMutation({
      mutationFn: ({
        id,
        patch,
      }: {
        id: string;
        patch: {
          name?: string;
          parentId?: string | null;
          moveToParent?: boolean;
          position?: number;
        };
      }) => orgApi.updateCategory(id, patch),
      onSettled: invalidate,
    }),
    remove: useMutation({
      mutationFn: (id: string) => orgApi.deleteCategory(id),
      onSuccess: invalidate,
    }),
  };
}

export function useTenantSettings() {
  return useQuery({ queryKey: queryKeys.tenant, queryFn: orgApi.tenant });
}

export function useUpdateTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (patch: TenantPatch) => orgApi.updateTenant(patch),
    onSuccess: (tenant) => {
      queryClient.setQueryData(queryKeys.tenant, tenant);
      const me = useAuthStore.getState().me;
      if (me)
        useAuthStore
          .getState()
          .setMe({ ...me, tenant: { ...me.tenant, name: tenant.name, branding: tenant.branding } });
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: queryKeys.tenant }),
  });
}

export function useAuditLog(params: Omit<AuditQuery, 'cursor'>) {
  return useInfiniteQuery({
    queryKey: queryKeys.auditLog(params),
    queryFn: ({ pageParam }) => orgApi.auditLog({ ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

export function useOrders(courseId?: string) {
  return useInfiniteQuery({
    queryKey: queryKeys.orders({ courseId }),
    queryFn: ({ pageParam }) => billingApi.orders({ courseId, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

export function useIntegrations() {
  const queryClient = useQueryClient();
  const tokens = useQuery({ queryKey: queryKeys.tokens, queryFn: integrationsApi.tokens });
  const webhooks = useQuery({ queryKey: queryKeys.webhooks, queryFn: integrationsApi.webhooks });
  const invalidateTokens = () => void queryClient.invalidateQueries({ queryKey: queryKeys.tokens });
  const invalidateWebhooks = () =>
    void queryClient.invalidateQueries({ queryKey: queryKeys.webhooks });
  return {
    tokens,
    webhooks,
    createToken: useMutation({
      mutationFn: integrationsApi.createToken,
      onSuccess: invalidateTokens,
    }),
    revokeToken: useMutation({
      mutationFn: integrationsApi.revokeToken,
      onSuccess: invalidateTokens,
    }),
    createWebhook: useMutation({
      mutationFn: integrationsApi.createWebhook,
      onSuccess: invalidateWebhooks,
    }),
    deleteWebhook: useMutation({
      mutationFn: integrationsApi.deleteWebhook,
      onSuccess: invalidateWebhooks,
    }),
  };
}

export function useDeliveries(webhookId: string | null) {
  return useInfiniteQuery({
    queryKey: queryKeys.deliveries(webhookId ?? 'none'),
    queryFn: ({ pageParam }) => integrationsApi.deliveries(webhookId as string, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
    enabled: !!webhookId,
  });
}
