import { create } from 'zustand';

const STORAGE_KEY = 'tc_admin_tenant';
const LOG_SCOPE_KEY = 'tc_admin_log_scope';

/** Whose records the admin logs show: every school at once or only the school picked in the header. */
export type AdminLogScope = 'all' | 'school';

type AdminTenantState = {
  /** School the platform administrator is managing in /admin; sent as `X-Tenant-Id`. */
  tenantId: string | null;
  setTenantId: (tenantId: string | null) => void;
  logScope: AdminLogScope;
  setLogScope: (scope: AdminLogScope) => void;
};

function readStored(key: string): string | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStored(key: string, value: string | null) {
  try {
    if (value) window.localStorage.setItem(key, value);
    else window.localStorage.removeItem(key);
  } catch {
    // Storage unavailable: the choice lives for this tab only.
  }
}

export const useAdminTenantStore = create<AdminTenantState>((set) => ({
  tenantId: readStored(STORAGE_KEY),
  setTenantId: (tenantId) => {
    writeStored(STORAGE_KEY, tenantId);
    set({ tenantId });
  },
  logScope: readStored(LOG_SCOPE_KEY) === 'school' ? 'school' : 'all',
  setLogScope: (logScope) => {
    writeStored(LOG_SCOPE_KEY, logScope);
    set({ logScope });
  },
}));
