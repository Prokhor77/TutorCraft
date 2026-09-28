'use client';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, type ReactNode } from 'react';
import { AppShell } from '@/components/layout/app-shell';
import { FormulaDialogHost } from '@/components/math/formula-dialog-host';
import { Spinner } from '@/components/ui/spinner';
import { persistLocale } from '@/features/app/locale';
import { useRequireAuth, useSessionBootstrap, useTenantBranding } from '@/features/auth/use-auth';
import { useOfflineRunner } from '@/features/offline/use-offline-runner';
import { useRealtime } from '@/features/realtime/use-realtime';
import { useAuthStore } from '@/stores/auth-store';

/** Keeps the UI language in sync with the profile locale after login on another device. */
function useLocaleSync(): void {
  const router = useRouter();
  const current = useLocale();
  const preferred = useAuthStore((state) => state.me?.locale);
  useEffect(() => {
    if (!preferred || preferred === current) return;
    if (persistLocale(preferred)) router.refresh();
  }, [preferred, current, router]);
}

function AppRuntime({ children }: { children: ReactNode }) {
  useRealtime();
  useOfflineRunner();
  useTenantBranding();
  useLocaleSync();
  return (
    <>
      <AppShell>{children}</AppShell>
      <FormulaDialogHost />
    </>
  );
}

/** Client-side auth guard for the (app) route group (ADR-003: no authenticated SSR). */
export function AuthenticatedApp({ children }: { children: ReactNode }) {
  const t = useTranslations('common');
  useSessionBootstrap();
  useRequireAuth();
  const status = useAuthStore((state) => state.status);
  if (status !== 'authenticated') {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Spinner label={t('loading')} />
      </div>
    );
  }
  return <AppRuntime>{children}</AppRuntime>;
}
