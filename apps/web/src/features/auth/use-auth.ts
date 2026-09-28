'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect } from 'react';
import {
  authApi,
  type LoginInput,
  type RegisterInput,
  type AcceptInvitationInput,
} from '@/lib/api/endpoints/auth';
import { meApi, type UpdateMeInput } from '@/lib/api/endpoints/me';
import { http, SESSION_EXPIRED_EVENT } from '@/lib/api/http';
import type { AuthResponse, TelegramAuthPayload } from '@/lib/api/schemas/auth';
import { applyBrandColor } from '@/lib/utils/color';
import { useAuthStore } from '@/stores/auth-store';
import { queryKeys } from '../query-keys';
import { persistLocale } from '../app/locale';
import { loginUrlWithNext, ROUTES } from './routes';

/** Restores the session from the refresh cookie on first load (access token is memory-only). */
export function useSessionBootstrap(): void {
  const status = useAuthStore((state) => state.status);
  useEffect(() => {
    if (status !== 'unknown') return;
    http.refreshSession().then((outcome) => {
      if (outcome !== 'refreshed') useAuthStore.getState().clearSession();
    });
  }, [status]);
}

/** Redirects to /login?next=… when the session is gone (guard for the authenticated app). */
export function useRequireAuth(): void {
  const router = useRouter();
  const status = useAuthStore((state) => state.status);
  useEffect(() => {
    const redirect = () =>
      router.replace(loginUrlWithNext(window.location.pathname + window.location.search));
    if (status === 'anonymous') redirect();
    window.addEventListener(SESSION_EXPIRED_EVENT, redirect);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, redirect);
  }, [status, router]);
}

/** FR-ADMIN-01: tenant primary color overrides the --primary token at runtime. */
export function useTenantBranding(): void {
  const color = useAuthStore((state) => state.me?.tenant.branding.primaryColor ?? null);
  useEffect(() => applyBrandColor(color), [color]);
}

export function useAuthProviders() {
  return useQuery({
    queryKey: queryKeys.providers,
    queryFn: authApi.providers,
    staleTime: Infinity,
  });
}

function useOnAuthenticated() {
  const queryClient = useQueryClient();
  return useCallback(
    (auth: AuthResponse) => {
      queryClient.clear();
      useAuthStore.getState().setSession(auth);
      persistLocale(auth.user.locale);
    },
    [queryClient],
  );
}

const SILENT = { skipErrorToast: true } as const;

export function useLogin() {
  const onAuthenticated = useOnAuthenticated();
  return useMutation({
    mutationFn: (input: LoginInput) => authApi.login(input),
    onSuccess: onAuthenticated,
    meta: SILENT,
  });
}

export function useGoogleLogin() {
  const onAuthenticated = useOnAuthenticated();
  return useMutation({
    mutationFn: (input: { idToken: string; tenantSlug?: string }) => authApi.google(input),
    onSuccess: onAuthenticated,
    meta: SILENT,
  });
}

export function useTelegramLogin() {
  const onAuthenticated = useOnAuthenticated();
  return useMutation({
    mutationFn: (input: TelegramAuthPayload & { tenantSlug?: string }) => authApi.telegram(input),
    onSuccess: onAuthenticated,
    meta: SILENT,
  });
}

export function useRegister() {
  const onAuthenticated = useOnAuthenticated();
  return useMutation({
    mutationFn: (input: RegisterInput) => authApi.register(input),
    onSuccess: onAuthenticated,
    meta: SILENT,
  });
}

export function useAcceptInvitation() {
  const onAuthenticated = useOnAuthenticated();
  return useMutation({
    mutationFn: (input: AcceptInvitationInput) => authApi.acceptInvitation(input),
    onSuccess: onAuthenticated,
    meta: SILENT,
  });
}

export function useLogout() {
  const router = useRouter();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (everywhere: boolean) =>
      everywhere ? authApi.logoutAll().then(() => authApi.logout()) : authApi.logout(),
    onSettled: () => {
      useAuthStore.getState().clearSession();
      queryClient.clear();
      clearServiceWorkerUserCache();
      router.replace(ROUTES.login);
    },
  });
}

/** Private cached API responses must not outlive the session on shared devices (see public/sw.js). */
function clearServiceWorkerUserCache(): void {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return;
  navigator.serviceWorker.controller?.postMessage({ type: 'tc:clear-user-cache' });
}

export function useUpdateMe() {
  return useMutation({
    mutationFn: (input: UpdateMeInput) => meApi.update(input),
    onSuccess: (me) => {
      useAuthStore.getState().setMe(me);
      persistLocale(me.locale);
    },
  });
}

export function useMe() {
  return useAuthStore((state) => state.me);
}
