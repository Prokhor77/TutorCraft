'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { ROUTES } from '@/features/auth/routes';
import { cn } from '@/lib/utils/cn';

const TABS = [
  { href: ROUTES.login, key: 'loginTab' },
  { href: ROUTES.register, key: 'registerTab' },
] as const;

/** Stitch auth card switch «Вход · Регистрация» (links, so each screen keeps its own URL). */
export function AuthTabs() {
  const t = useTranslations('auth');
  const pathname = usePathname();
  if (!TABS.some((tab) => tab.href === pathname)) return null;
  return (
    <nav aria-label={t('authTabs')} className="mb-6">
      <ul className="grid grid-cols-2 gap-1 rounded-full bg-surface-muted p-1">
        {TABS.map((tab) => {
          const active = tab.href === pathname;
          return (
            <li key={tab.href}>
              <Link
                href={tab.href}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'flex h-10 items-center justify-center rounded-full text-label-lg transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
                  active
                    ? 'bg-surface text-primary shadow-sm'
                    : 'text-text-muted hover:text-primary',
                )}
              >
                {t(tab.key)}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
