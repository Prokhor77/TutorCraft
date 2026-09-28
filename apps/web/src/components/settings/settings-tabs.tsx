'use client';
import { Bell, UserRound } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { ROUTES } from '@/features/auth/routes';
import { cn } from '@/lib/utils/cn';

const TABS = [
  { href: ROUTES.profile, key: 'profile', icon: UserRound },
  { href: ROUTES.notificationSettings, key: 'notificationSettings', icon: Bell },
] as const;

/** Stitch pill-tab row switching between personal settings pages (links, not ARIA tabs). */
export function SettingsTabs() {
  const t = useTranslations('nav');
  const tProfile = useTranslations('profile');
  const pathname = usePathname();
  return (
    <nav aria-label={tProfile('settingsNav')}>
      <ul className="inline-flex max-w-full items-center gap-1 overflow-x-auto scrollbar-none rounded-full bg-surface-muted p-1 shadow-inner">
        {TABS.map(({ href, key, icon: Icon }) => {
          const active = pathname === href;
          return (
            <li key={href}>
              <Link
                href={href}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'inline-flex h-8 items-center gap-1.5 whitespace-nowrap rounded-full px-3 text-label-lg transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 sm:px-4 [&_svg]:size-4',
                  active
                    ? 'bg-primary text-primary-foreground shadow-sm'
                    : 'text-text-muted hover:bg-surface-container hover:text-text',
                )}
              >
                <Icon aria-hidden /> {t(key)}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
