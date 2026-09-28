import { create } from 'zustand';

export const THEMES = ['system', 'light', 'dark'] as const;
export type ThemePreference = (typeof THEMES)[number];
export const THEME_COOKIE = 'tc_theme';
const THEME_COOKIE_MAX_AGE_SEC = 60 * 60 * 24 * 365;

export const COUNTER_NAMES = {
  gradingQueue: 'grading_queue',
  unreadNotifications: 'unread_notifications',
} as const;

type UiState = {
  theme: ThemePreference;
  /** Realtime counters from WebSocket `counter` messages (API-08). */
  counters: Record<string, number>;
  /** Teacher "view as student" preview (client-side only). */
  viewAsStudent: boolean;
  setTheme: (theme: ThemePreference) => void;
  setCounter: (name: string, value: number) => void;
  setViewAsStudent: (value: boolean) => void;
};

function applyTheme(theme: ThemePreference): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  if (theme === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', theme);
  document.cookie = `${THEME_COOKIE}=${theme}; path=/; max-age=${THEME_COOKIE_MAX_AGE_SEC}; samesite=lax`;
}

function initialTheme(): ThemePreference {
  if (typeof document === 'undefined') return 'system';
  const attr = document.documentElement.getAttribute('data-theme');
  return attr === 'light' || attr === 'dark' ? attr : 'system';
}

export const useUiStore = create<UiState>((set) => ({
  theme: initialTheme(),
  counters: {},
  viewAsStudent: false,
  setTheme: (theme) => {
    applyTheme(theme);
    set({ theme });
  },
  setCounter: (name, value) => set((state) => ({ counters: { ...state.counters, [name]: value } })),
  setViewAsStudent: (viewAsStudent) => set({ viewAsStudent }),
}));
