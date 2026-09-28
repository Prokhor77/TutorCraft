'use client';
import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import {
  Activity,
  Building2,
  FolderTree,
  Lock,
  Palette,
  PlugZap,
  Receipt,
  ScrollText,
  ShieldCheck,
  UserCog,
  Users,
  type LucideIcon,
} from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, type ReactNode } from 'react';
import { TenantPicker } from '@/components/admin/tenant-picker';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Skeleton } from '@/components/ui/skeleton';
import { Breadcrumbs, PageHeader } from '@/components/ui/page-header';
import { usePlatformTenants } from '@/features/admin/use-admin';
import { ROUTES } from '@/features/auth/routes';
import { isPlatformAdminHint } from '@/lib/access/permissions';
import { cn } from '@/lib/utils/cn';
import { useAdminTenantStore } from '@/stores/admin-tenant-store';
import { selectMe, useAuthStore } from '@/stores/auth-store';

const SECTIONS: readonly { href: string; key: string; icon: LucideIcon }[] = [
  { href: ROUTES.adminUsers, key: 'users', icon: Users },
  { href: ROUTES.adminAccounts, key: 'accounts', icon: UserCog },
  { href: ROUTES.adminCategories, key: 'categories', icon: FolderTree },
  { href: ROUTES.adminBranding, key: 'branding', icon: Palette },
  { href: ROUTES.adminAudit, key: 'audit', icon: ScrollText },
  { href: ROUTES.adminActivity, key: 'activity', icon: Activity },
  { href: ROUTES.adminOrders, key: 'orders', icon: Receipt },
  { href: ROUTES.adminIntegrations, key: 'integrations', icon: PlugZap },
];

/** Session-wide queries survive a school switch; everything else was fetched for the previous school. */
const KEEP_ON_TENANT_SWITCH = new Set(['me', 'auth', 'notifications', 'platform']);

function resetSchoolQueries(queryClient: QueryClient) {
  void queryClient.resetQueries({
    predicate: (query) => !KEEP_ON_TENANT_SWITCH.has(String(query.queryKey[0])),
  });
}

/**
 * Admin area (SPEC §10) of the single platform administrator (ADMIN_EMAIL on the server). Every section works inside
 * the school picked in the header; requests carry it as `X-Tenant-Id`. Everyone else gets a no-access state — the
 * API refuses them anyway.
 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  const t = useTranslations('admin');
  const me = useAuthStore(selectMe);
  if (!isPlatformAdminHint(me?.tenantRoles)) {
    return (
      <EmptyState
        icon={Lock}
        title={t('forbidden.title')}
        description={t('forbidden.description')}
      />
    );
  }
  return <PlatformAdminLayout>{children}</PlatformAdminLayout>;
}

function PlatformAdminLayout({ children }: { children: ReactNode }) {
  const t = useTranslations('admin');
  const tNav = useTranslations('nav');
  const tShell = useTranslations('shell');
  const tCommon = useTranslations('common');
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const tenants = usePlatformTenants();
  const tenantId = useAdminTenantStore((state) => state.tenantId);
  const setTenantId = useAdminTenantStore((state) => state.setTenantId);
  const list = tenants.data ?? [];
  const selected = list.find((tenant) => tenant.id === tenantId);
  const current = SECTIONS.find((section) => pathname.startsWith(section.href));

  // Stored school vanished (or first visit): fall back to the newest one.
  useEffect(() => {
    if (tenants.data && !selected) setTenantId(tenants.data[0]?.id ?? null);
  }, [tenants.data, selected, setTenantId]);

  // Leaving /admin: drop data fetched for the managed school so the rest of the app refetches its own.
  useEffect(() => () => resetSchoolQueries(queryClient), [queryClient]);

  const switchTenant = (next: string) => {
    if (next === tenantId) return;
    setTenantId(next);
    resetSchoolQueries(queryClient);
  };

  const body = tenants.isError ? (
    <ErrorState
      title={t('tenant.loadError')}
      retryLabel={tCommon('retry')}
      onRetry={() => void tenants.refetch()}
    />
  ) : !tenants.data ? (
    <Skeleton className="h-64 w-full" />
  ) : !selected ? (
    <EmptyState icon={Building2} title={t('tenant.empty')} description={t('tenant.choose')} />
  ) : (
    <div key={selected.id} className="contents">
      {children}
    </div>
  );

  return (
    <>
      <PageHeader
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[
              { label: tNav('home'), href: ROUTES.home },
              { label: t('title'), href: current ? ROUTES.admin : undefined },
              ...(current ? [{ label: t(`nav.${current.key}`) }] : []),
            ]}
          />
        }
        eyebrow={selected?.name}
        title={t('title')}
        meta={
          <Badge tone="primary">
            <ShieldCheck aria-hidden /> {t('adminChip')}
          </Badge>
        }
        description={t('description')}
        actions={
          tenants.data?.length ? (
            <TenantPicker
              tenants={tenants.data}
              value={selected?.id ?? null}
              onChange={switchTenant}
            />
          ) : null
        }
      >
        <nav
          aria-label={t('sections')}
          className="scrollbar-none -mx-1 max-w-full overflow-x-auto px-1"
        >
          <ul className="inline-flex gap-1 rounded-full bg-surface-muted p-1 shadow-inner">
            {SECTIONS.map((section) => {
              const active = section === current;
              const Icon = section.icon;
              return (
                <li key={section.href}>
                  <Link
                    href={section.href}
                    aria-current={active ? 'page' : undefined}
                    className={cn(
                      'flex h-9 items-center gap-2 whitespace-nowrap rounded-full px-4 text-label-lg transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                      active
                        ? 'bg-primary text-primary-foreground shadow-sm'
                        : 'text-text-muted hover:bg-surface-container hover:text-text',
                    )}
                  >
                    <Icon className="size-4 shrink-0" aria-hidden />
                    {t(`nav.${section.key}`)}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
      </PageHeader>
      {body}
    </>
  );
}
