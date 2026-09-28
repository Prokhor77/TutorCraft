import { create } from 'zustand';

const STORAGE_KEY = 'tc_admin_tenant';

type AdminTenantState = {
  /** School the platform administrator is managing in /admin; sent as `X-Tenant-Id`. */
  tenantId: string | null;
  setTenantId: (tenantId: string | null) => void;
};

function readStored(): string | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export const useAdminTenantStore = create<AdminTenantState>((set) => ({
  tenantId: readStored(),
  setTenantId: (tenantId) => {
    try {
      if (tenantId) window.localStorage.setItem(STORAGE_KEY, tenantId);
      else window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Storage unavailable: the choice lives for this tab only.
    }
    set({ tenantId });
  },
}));
