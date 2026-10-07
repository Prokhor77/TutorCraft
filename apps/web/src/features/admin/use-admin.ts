'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { integrationsApi } from '@/lib/api/endpoints/integrations';
import { platformApi, type PlatformCoursesQuery } from '@/lib/api/endpoints/platform';
import {
  orgApi,
  type AuditQuery,
  type CreateUserInput,
  type TenantPatch,
  type UserPatch,
  type UsersQuery,
} from '@/lib/api/endpoints/org';
import { getNextCursor } from '@/lib/api/pagination';
import { useAdminTenantStore } from '@/stores/admin-tenant-store';
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

/** Schools the platform administrator can manage (tenant picker in /admin). */
export function usePlatformTenants(enabled = true) {
  return useQuery({ queryKey: queryKeys.platformTenants, queryFn: platformApi.tenants, enabled });
}

/**
 * Irreversible deletion of a school with all its accounts (the owner included), courses and files. The school picked
 * in the header is dropped when it is the deleted one; the layout then falls back to the newest remaining school.
 */
export function useDeleteTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ tenantId, confirmSlug }: { tenantId: string; confirmSlug: string }) =>
      platformApi.deleteTenant(tenantId, confirmSlug),
    onSuccess: (_, { tenantId }) => {
      const store = useAdminTenantStore.getState();
      if (store.tenantId === tenantId) store.setTenantId(null);
      void queryClient.invalidateQueries({ queryKey: ['platform'] });
      void queryClient.invalidateQueries({ queryKey: ['audit-log'] });
      void queryClient.invalidateQueries({ queryKey: ['activity-log'] });
    },
  });
}

/** Courses of every school (or of one), newest first — the admin «Курсы» tab. */
export function usePlatformCourses(params: Omit<PlatformCoursesQuery, 'cursor'>) {
  return useInfiniteQuery({
    queryKey: queryKeys.platformCourses(params),
    queryFn: ({ pageParam }) => platformApi.courses({ ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

/** Space taken by every school (all uploads, as counted against the quota). */
export function usePlatformStorage() {
  return useQuery({ queryKey: queryKeys.platformStorage, queryFn: platformApi.storage });
}

/** Space taken by the course materials of one school. */
export function usePlatformCourseStorage(tenantId: string | null) {
  return useQuery({
    queryKey: queryKeys.platformCourseStorage(tenantId ?? 'none'),
    queryFn: () => platformApi.courseStorage(tenantId as string),
    enabled: !!tenantId,
  });
}

/** Trial and paid access of every school whose trial has started. */
export function usePlatformSubscriptions() {
  return useQuery({
    queryKey: queryKeys.platformSubscriptions,
    queryFn: platformApi.subscriptions,
  });
}

/** Moves the end of a school's free (trial) access. */
export function useSetTrialEnd() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      tenantId,
      trialEndsAt,
      version,
    }: {
      tenantId: string;
      trialEndsAt: string;
      version: number;
    }) => platformApi.setTrialEnd(tenantId, { trialEndsAt, version }),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: queryKeys.platformSubscriptions }),
  });
}
