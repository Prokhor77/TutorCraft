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
      <nav aria-label={t('sections')} className="mb-6 max-w-full overflow-x-auto">
        <div className="inline-flex gap-1 rounded-full bg-surface-muted p-1">
          {SECTIONS.map((section) => {
            const active = pathname.startsWith(section.href);
            return (
              <Link
                key={section.href}
                href={section.href}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'flex h-9 items-center whitespace-nowrap rounded-full px-4 text-label-lg transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                  active
                    ? 'bg-surface text-primary shadow-sm'
                    : 'text-text-muted hover:text-primary',
                )}
              >
                {t(`nav.${section.key}`)}
              </Link>
            );
          })}
        </div>
      </nav>
      {children}
    </>
  );
}
