'use client';
import { usePathname, useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, type ReactNode } from 'react';
import { AppShell } from '@/components/layout/app-shell';
import { FormulaDialogHost } from '@/components/math/formula-dialog-host';
import { Spinner } from '@/components/ui/spinner';
import { persistLocale } from '@/features/app/locale';
import { isActivePath } from '@/features/app/navigation';
import { ROUTES } from '@/features/auth/routes';
import { useRequireAuth, useSessionBootstrap, useTenantBranding } from '@/features/auth/use-auth';
import { useOfflineRunner } from '@/features/offline/use-offline-runner';
import { useRealtime } from '@/features/realtime/use-realtime';
import { isPlatformAdminHint } from '@/lib/access/permissions';
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

/** What the platform administrator may open besides the admin console: own profile and notifications. */
const PLATFORM_ADMIN_PATHS = [
  ROUTES.admin,
  ROUTES.profile,
  ROUTES.notificationSettings,
  ROUTES.notifications,
];

/**
 * The platform administrator's account is for the admin console only: courses, grades, calendar and the rest of the
 * school UI redirect to /admin (the API refuses course creation anyway). @returns whether the page may render
 */
function usePlatformAdminConfinement(): boolean {
  const router = useRouter();
  const pathname = usePathname();
  const isAdmin = useAuthStore((state) => isPlatformAdminHint(state.me?.tenantRoles));
  const allowed = !isAdmin || PLATFORM_ADMIN_PATHS.some((path) => isActivePath(pathname, path));
  useEffect(() => {
    if (!allowed) router.replace(ROUTES.admin);
  }, [allowed, router]);
  return allowed;
}

function AppRuntime({ children }: { children: ReactNode }) {
  const t = useTranslations('common');
  useRealtime();
  useOfflineRunner();
  useTenantBranding();
  useLocaleSync();
  const allowed = usePlatformAdminConfinement();
  return (
    <>
      <AppShell>
        {allowed ? (
          children
        ) : (
          <div className="flex min-h-64 items-center justify-center">
            <Spinner label={t('loading')} />
          </div>
        )}
      </AppShell>
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
