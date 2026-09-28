import { create } from 'zustand';
import type { AuthResponse, Me } from '@/lib/api/schemas/auth';

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous';

type AuthState = {
  /** Access JWT lives only in memory (ADR-003). Never persisted, never logged. */
  accessToken: string | null;
  me: Me | null;
  status: AuthStatus;
  setSession: (auth: AuthResponse) => void;
  setMe: (me: Me) => void;
  clearSession: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  me: null,
  status: 'unknown',
  setSession: (auth) =>
    set({ accessToken: auth.accessToken, me: auth.user, status: 'authenticated' }),
  setMe: (me) => set({ me }),
  clearSession: () => set({ accessToken: null, me: null, status: 'anonymous' }),
}));

export const selectMe = (state: AuthState) => state.me;
export const selectAuthStatus = (state: AuthState) => state.status;
