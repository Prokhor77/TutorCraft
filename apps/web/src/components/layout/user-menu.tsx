'use client';
import { Bell, ChevronDown, LogOut, Settings, User } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Avatar } from '@/components/ui/avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { ROUTES } from '@/features/auth/routes';
import { persistLocale } from '@/features/app/locale';
import { buildNavigation } from '@/features/app/navigation';
import { useLogout, useMe, useUpdateMe } from '@/features/auth/use-auth';
import { roleHints, useMyCourses } from '@/features/courses/use-my-courses';
import { LOCALES } from '@/i18n/config';
import { THEMES, useUiStore } from '@/stores/ui-store';
import { isPlatformAdminHint } from '@/lib/access/permissions';
import { fullName } from '@/lib/utils/format';

export function UserMenu() {
  const t = useTranslations('nav');
  const me = useMe();
  const logout = useLogout();
  const tPrefs = useTranslations('preferences');
  const locale = useLocale();
  const router = useRouter();
  const updateMe = useUpdateMe();
  const theme = useUiStore((state) => state.theme);
  const setTheme = useUiStore((state) => state.setTheme);
  const { data: courses } = useMyCourses();
  const navigation = buildNavigation({
    ...roleHints(courses),
    isAdmin: isPlatformAdminHint(me?.tenantRoles),
  });
  if (!me) return null;
  const name = fullName(me);
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className="flex items-center gap-1.5 rounded-full p-0.5 transition-colors duration-fast hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/25 2xl:pr-2"
        aria-label={t('account')}
      >
        <Avatar name={name} src={me.avatarUrl} size="sm" />
        {/* Stitch user chip: name + chevron on wide screens. */}
        <span className="hidden max-w-32 truncate text-label-lg text-text 2xl:inline">
          {me.firstName}
        </span>
        <ChevronDown className="hidden size-4 text-text-muted 2xl:block" aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>
          <span className="block text-sm font-semibold text-text">{name}</span>
          <span className="block truncate">{me.tenant.name}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuLabel>{t('sections')}</DropdownMenuLabel>
        {navigation.map((item) => (
          <DropdownMenuItem key={item.href} asChild>
            <Link href={item.href}>
              <item.icon aria-hidden /> {t(item.labelKey)}
            </Link>
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        {/* Theme/language live in the header only on wide screens (2xl); elsewhere they are here. */}
        <div className="2xl:hidden">
          <DropdownMenuLabel>{tPrefs('theme')}</DropdownMenuLabel>
          {THEMES.map((option) => (
            <DropdownMenuItem
              key={option}
              role="menuitemradio"
              aria-checked={theme === option}
              onSelect={() => setTheme(option)}
            >
              {tPrefs(`themes.${option}`)}
            </DropdownMenuItem>
          ))}
          <DropdownMenuLabel>{tPrefs('language')}</DropdownMenuLabel>
          {LOCALES.map((option) => (
            <DropdownMenuItem
              key={option}
              role="menuitemradio"
              aria-checked={locale === option}
              onSelect={() => {
                persistLocale(option);
                updateMe.mutate({ locale: option });
                router.refresh();
              }}
            >
              {tPrefs(`languages.${option}`)}
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
        </div>
        <DropdownMenuItem asChild>
          <Link href={ROUTES.profile}>
            <User aria-hidden /> {t('profile')}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href={ROUTES.notificationSettings}>
            <Bell aria-hidden /> {t('notificationSettings')}
          </Link>
        </DropdownMenuItem>
        {isPlatformAdminHint(me.tenantRoles) ? (
          <DropdownMenuItem asChild>
            <Link href={ROUTES.admin}>
              <Settings aria-hidden /> {t('admin')}
            </Link>
          </DropdownMenuItem>
        ) : null}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => logout.mutate(false)}>
          <LogOut aria-hidden /> {t('logout')}
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => logout.mutate(true)}>
          <LogOut aria-hidden /> {t('logoutAll')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
