'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { CountBadge } from '@/components/ui/badge';
import { buildNavigation, isActivePath, type NavItem } from '@/features/app/navigation';
import { ROUTES } from '@/features/auth/routes';
import { useMe, useUpdateMe } from '@/features/auth/use-auth';
import { roleHints, useMyCourses } from '@/features/courses/use-my-courses';
import { isTenantAdminHint } from '@/lib/access/permissions';
import { cn } from '@/lib/utils/cn';
import { useUiStore } from '@/stores/ui-store';
import { Brand } from './brand';
import { NotificationsBell } from './notifications-bell';
import { LanguageMenu, ThemeMenu } from './preferences-menu';
import { MAIN_CONTENT_ID, SkipLink } from './skip-link';
import { UserMenu } from './user-menu';

function NavLink({
  item,
  pathname,
  compact,
}: {
  item: NavItem;
  pathname: string;
  compact?: boolean;
}) {
  const t = useTranslations('nav');
  const counter = useUiStore((state) => (item.counter ? (state.counters[item.counter] ?? 0) : 0));
  const active = isActivePath(pathname, item.href);
  const Icon = item.icon;
  return (
    <Link
      href={item.href}
      aria-current={active ? 'page' : undefined}
      className={cn(
        'relative flex items-center rounded-md text-sm font-medium transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
        compact ? 'flex-1 flex-col gap-1 py-2 text-[11px]' : 'gap-3 px-3 py-2',
        active
          ? 'bg-primary-soft text-primary'
          : 'text-text-muted hover:bg-surface-muted hover:text-text',
        compact && active && 'bg-transparent',
      )}
    >
      <Icon className={compact ? 'size-5' : 'size-4'} aria-hidden />
      <span className="truncate">{t(item.labelKey)}</span>
      {counter > 0 ? (
        <CountBadge
          count={counter}
          label={t('counter', { count: counter })}
          className={compact ? 'absolute right-1/4 top-1' : 'ml-auto'}
        />
      ) : null}
    </Link>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const t = useTranslations('nav');
  const pathname = usePathname();
  const me = useMe();
  const updateMe = useUpdateMe();
  const { data: courses } = useMyCourses();
  const navigation = buildNavigation({
    ...roleHints(courses),
    isAdmin: isTenantAdminHint(me?.tenantRoles),
  });
  const tenantName = me?.tenant.name;

  return (
    <div className="min-h-dvh bg-background">
      <SkipLink label={t('skipToContent')} />
      <aside
        className="fixed inset-y-0 left-0 z-30 hidden w-sidebar flex-col border-r border-border bg-surface md:flex"
        aria-label={t('primary')}
      >
        <div className="flex h-header items-center px-4">
          <Brand href={ROUTES.home} name={tenantName} logoUrl={me?.tenant.branding.logoUrl} />
        </div>
        <nav className="flex flex-1 flex-col gap-1 overflow-y-auto p-3">
          {navigation.map((item) => (
            <NavLink key={item.href} item={item} pathname={pathname} />
          ))}
        </nav>
      </aside>
      <div className="md:pl-sidebar">
        <header className="sticky top-0 z-20 flex h-header items-center gap-2 border-b border-border bg-surface/90 px-page-x backdrop-blur">
          <Brand
            href={ROUTES.home}
            name={tenantName}
            logoUrl={me?.tenant.branding.logoUrl}
            className="md:hidden"
          />
          <div className="ml-auto flex items-center gap-1">
            <LanguageMenu onChange={(locale) => updateMe.mutate({ locale })} />
            <ThemeMenu />
            <NotificationsBell />
            <UserMenu />
          </div>
        </header>
        <main
          id={MAIN_CONTENT_ID}
          tabIndex={-1}
          className="pb-safe-nav mx-auto w-full max-w-content px-page-x py-page-y focus:outline-none md:pb-page-y"
        >
          {children}
        </main>
      </div>
      <nav
        aria-label={t('primary')}
        className="fixed inset-x-0 bottom-0 z-30 flex h-bottom-nav items-stretch border-t border-border bg-surface px-1 pb-[env(safe-area-inset-bottom)] md:hidden"
      >
        {navigation
          .filter((item) => item.mobile)
          .map((item) => (
            <NavLink key={item.href} item={item} pathname={pathname} compact />
          ))}
      </nav>
    </div>
  );
}
