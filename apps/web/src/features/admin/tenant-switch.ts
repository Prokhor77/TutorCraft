import type { QueryClient } from '@tanstack/react-query';
import { useAdminTenantStore } from '@/stores/admin-tenant-store';

/** Session-wide queries survive a school switch; everything else was fetched for the previous school. */
const KEEP_ON_TENANT_SWITCH = new Set(['me', 'auth', 'notifications', 'platform']);

export function resetSchoolQueries(queryClient: QueryClient) {
  void queryClient.resetQueries({
    predicate: (query) => !KEEP_ON_TENANT_SWITCH.has(String(query.queryKey[0])),
  });
}

/** Makes /admin act in `tenantId` (sent as `X-Tenant-Id`) and drops data fetched for the previous school. */
export function switchAdminTenant(queryClient: QueryClient, tenantId: string) {
  const store = useAdminTenantStore.getState();
  if (store.tenantId === tenantId) return;
  store.setTenantId(tenantId);
  resetSchoolQueries(queryClient);
}
