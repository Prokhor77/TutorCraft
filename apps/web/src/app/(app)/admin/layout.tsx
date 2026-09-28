'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { PageHeader } from '@/components/ui/page-header';
import { ROUTES } from '@/features/auth/routes';
import { cn } from '@/lib/utils/cn';

const SECTIONS = [
  { href: ROUTES.adminUsers, key: 'users' },
  { href: ROUTES.adminCategories, key: 'categories' },
  { href: ROUTES.adminBranding, key: 'branding' },
  { href: ROUTES.adminAudit, key: 'audit' },
  { href: ROUTES.adminOrders, key: 'orders' },
  { href: ROUTES.adminIntegrations, key: 'integrations' },
] as const;

/** Tenant admin area (SPEC §10). Every endpoint is authorized server-side; 403s surface as errors. */
export default function AdminLayout({ children }: { children: ReactNode }) {
  const t = useTranslations('admin');
  const pathname = usePathname();
  return (
    <>
      <PageHeader title={t('title')} description={t('description')} />
      <nav
        aria-label={t('sections')}
        className="-mx-1 mb-6 flex gap-1 overflow-x-auto border-b border-border px-1"
      >
        {SECTIONS.map((section) => {
          const active = pathname.startsWith(section.href);
          return (
            <Link
              key={section.href}
              href={section.href}
              aria-current={active ? 'page' : undefined}
              className={cn(
                '-mb-px whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
                active
                  ? 'border-primary text-primary'
                  : 'border-transparent text-text-muted hover:text-text',
              )}
            >
              {t(`nav.${section.key}`)}
            </Link>
          );
        })}
      </nav>
      {children}
    </>
  );
}
