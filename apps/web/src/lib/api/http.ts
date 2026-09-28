import { useAdminTenantStore } from '@/stores/admin-tenant-store';
import { useAuthStore } from '@/stores/auth-store';
import { ApiClient } from './client';

export const SESSION_EXPIRED_EVENT = 'tc:session-expired';

function currentLocale(): string | undefined {
  if (typeof document === 'undefined') return undefined;
  return document.documentElement.lang || undefined;
}

/** Admin pages act in the school the platform administrator picked; every other page uses the own tenant. */
function adminTenantOverride(): string | undefined {
  if (typeof window === 'undefined' || !window.location.pathname.startsWith('/admin')) return undefined;
  return useAdminTenantStore.getState().tenantId ?? undefined;
}

/** Browser-side singleton wired to the in-memory auth store. */
export const http = new ApiClient({
  session: {
    getAccessToken: () => useAuthStore.getState().accessToken,
    setSession: (auth) => useAuthStore.getState().setSession(auth),
    clearSession: () => useAuthStore.getState().clearSession(),
  },
  getLocale: currentLocale,
  getTenantOverride: adminTenantOverride,
  onSessionExpired: () => {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  },
});
