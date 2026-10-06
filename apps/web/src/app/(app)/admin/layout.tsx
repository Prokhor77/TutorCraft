'use client';
import { useQueryClient } from '@tanstack/react-query';
import {
  Activity,
  BookOpen,
  Building2,
  FolderTree,
  HardDrive,
  Lock,
  Palette,
  PlugZap,
  School,
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
import { FilterChips } from '@/components/admin/admin-panel';
import { TenantPicker } from '@/components/admin/tenant-picker';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Skeleton } from '@/components/ui/skeleton';
import { Breadcrumbs, PageHeader } from '@/components/ui/page-header';
import { resetSchoolQueries, switchAdminTenant } from '@/features/admin/tenant-switch';
import { usePlatformTenants } from '@/features/admin/use-admin';
import { isActivePath } from '@/features/app/navigation';
import { ROUTES } from '@/features/auth/routes';
import { isPlatformAdminHint } from '@/lib/access/permissions';
import { cn } from '@/lib/utils/cn';
import { useAdminTenantStore, type AdminLogScope } from '@/stores/admin-tenant-store';
import { selectMe, useAuthStore } from '@/stores/auth-store';

type Section = { href: string; key: string; icon: LucideIcon };

/**
 * Admin tabs. `scope` says what a group works on: the whole platform, the school picked in the header, or — for the
 * logs — either of them (the «Все школы / Выбранная школа» switch).
 */
type Group = Section & { scope: 'platform' | 'school' | 'logs'; sections?: readonly Section[] };

const GROUPS: readonly Group[] = [
  { href: ROUTES.adminSchools, key: 'schools', icon: Building2, scope: 'platform' },
  { href: ROUTES.adminCourses, key: 'courses', icon: BookOpen, scope: 'platform' },
  { href: ROUTES.adminAccounts, key: 'accounts', icon: UserCog, scope: 'platform' },
  {
    href: ROUTES.adminAudit,
    key: 'logs',
    icon: ScrollText,
    scope: 'logs',
    sections: [
      { href: ROUTES.adminAudit, key: 'audit', icon: ScrollText },
      { href: ROUTES.adminActivity, key: 'activity', icon: Activity },
    ],
  },
  {
    href: ROUTES.adminUsers,
    key: 'school',
    icon: School,
    scope: 'school',
    sections: [
      { href: ROUTES.adminUsers, key: 'users', icon: Users },
      { href: ROUTES.adminCategories, key: 'categories', icon: FolderTree },
      { href: ROUTES.adminBranding, key: 'branding', icon: Palette },
      { href: ROUTES.adminIntegrations, key: 'integrations', icon: PlugZap },
    ],
  },
  { href: ROUTES.adminStorage, key: 'storage', icon: HardDrive, scope: 'platform' },
];

function groupOf(pathname: string): { group?: Group; section?: Section } {
  for (const group of GROUPS) {
    const section = group.sections?.find((entry) => isActivePath(pathname, entry.href));
    if (section || isActivePath(pathname, group.href)) return { group, section };
  }
  return {};
}

/**
 * Admin area (SPEC §10) of the single platform administrator (ADMIN_EMAIL on the server). Platform tabs (schools,
 * courses, accounts, storage) span every school; «Управление школой» works inside the school picked in the header (sent as
 * `X-Tenant-Id`); logs show every school or the picked one. Everyone else gets a no-access state — the API refuses
 * them anyway.
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
  const logScope = useAdminTenantStore((state) => state.logScope);
  const setLogScope = useAdminTenantStore((state) => state.setLogScope);
  const list = tenants.data ?? [];
  const selected = list.find((tenant) => tenant.id === tenantId);
  const { group, section } = groupOf(pathname);
  const needsTenant =
    group?.scope === 'school' || (group?.scope === 'logs' && logScope === 'school');

  // Stored school vanished (deleted, or first visit): fall back to the newest one.
  useEffect(() => {
    if (tenants.data && !selected) setTenantId(tenants.data[0]?.id ?? null);
  }, [tenants.data, selected, setTenantId]);

  // Leaving /admin: drop data fetched for the managed school so the rest of the app refetches its own.
  useEffect(() => () => resetSchoolQueries(queryClient), [queryClient]);

  const switchTenant = (next: string) => switchAdminTenant(queryClient, next);

  const schoolBody = tenants.isError ? (
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
              { label: t('title'), href: group ? ROUTES.admin : undefined },
              ...(group ? [{ label: t(`nav.${group.key}`) }] : []),
              ...(section ? [{ label: t(`nav.${section.key}`) }] : []),
            ]}
          />
        }
        eyebrow={needsTenant ? selected?.name : undefined}
        title={t('title')}
        meta={
          <Badge tone="primary">
            <ShieldCheck aria-hidden /> {t('adminChip')}
          </Badge>
        }
        description={t('description')}
        actions={
          needsTenant && tenants.data?.length ? (
            <TenantPicker
              tenants={tenants.data}
              value={selected?.id ?? null}
              onChange={switchTenant}
            />
          ) : null
        }
      >
        <div className="flex w-full min-w-0 flex-col gap-3">
          <nav
            aria-label={t('sections')}
            className="scrollbar-none -mx-1 max-w-full overflow-x-auto px-1"
          >
            <ul className="inline-flex gap-1 rounded-full bg-surface-muted p-1 shadow-inner">
              {GROUPS.map((entry) => {
                const active = entry === group;
                const Icon = entry.icon;
                return (
                  <li key={entry.key}>
                    <Link
                      href={entry.href}
                      aria-current={active ? 'page' : undefined}
                      className={cn(
                        'flex h-9 items-center gap-2 whitespace-nowrap rounded-full px-4 text-label-lg transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                        active
                          ? 'bg-primary text-primary-foreground shadow-sm'
                          : 'text-text-muted hover:bg-surface-container hover:text-text',
                      )}
                    >
                      <Icon className="size-4 shrink-0" aria-hidden />
                      {t(`nav.${entry.key}`)}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>
          {group?.sections ? (
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
              <SubNav
                label={t('subsections', { group: t(`nav.${group.key}`) })}
                sections={group.sections}
                active={section}
              />
              {group.scope === 'logs' ? (
                <FilterChips<AdminLogScope>
                  label={t('logScope.label')}
                  value={logScope}
                  onChange={setLogScope}
                  options={[
                    { value: 'all', label: t('logScope.all') },
                    { value: 'school', label: t('logScope.school') },
                  ]}
                />
              ) : null}
            </div>
          ) : null}
        </div>
      </PageHeader>
      {needsTenant ? schoolBody : children}
    </>
  );
}

/** Second row of tabs inside a group (e.g. users / categories / branding / integrations of the school). */
function SubNav({
  label,
  sections,
  active,
}: {
  label: string;
  sections: readonly Section[];
  active: Section | undefined;
}) {
  const t = useTranslations('admin');
  return (
    <nav aria-label={label} className="scrollbar-none -mx-1 max-w-full overflow-x-auto px-1">
      <ul className="flex gap-1.5">
        {sections.map((entry) => {
          const current = entry === active;
          const Icon = entry.icon;
          return (
            <li key={entry.href}>
              <Link
                href={entry.href}
                aria-current={current ? 'page' : undefined}
                className={cn(
                  'flex h-8 items-center gap-1.5 whitespace-nowrap rounded-full px-3.5 text-label-md transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                  current
                    ? 'bg-primary-soft text-primary'
                    : 'text-text-muted hover:bg-surface-muted hover:text-primary',
                )}
              >
                <Icon className="size-4 shrink-0" aria-hidden />
                {t(`nav.${entry.key}`)}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
