const STORAGE_KEY = 'tc_tab_session';

let memoryId: string | null = null;

function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function')
    return crypto.randomUUID();
  return `tab-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Random id of this browser tab: groups a user's actions in the activity log (including anonymous ones before login).
 * Lives in sessionStorage, so it survives reloads but not a new tab; falls back to memory when storage is blocked.
 */
export function getTabSessionId(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  try {
    const stored = window.sessionStorage.getItem(STORAGE_KEY);
    if (stored) return stored;
    const created = newId();
    window.sessionStorage.setItem(STORAGE_KEY, created);
    return created;
  } catch {
    memoryId ??= newId();
    return memoryId;
  }
}
