'use client';
import { Bell, LogOut, Settings, User } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
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
import { useLogout, useMe } from '@/features/auth/use-auth';
import { isTenantAdminHint } from '@/lib/access/permissions';
import { fullName } from '@/lib/utils/format';

export function UserMenu() {
  const t = useTranslations('nav');
  const me = useMe();
  const logout = useLogout();
  if (!me) return null;
  const name = fullName(me);
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className="rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
        aria-label={t('account')}
      >
        <Avatar name={name} src={me.avatarUrl} size="sm" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>
          <span className="block text-sm font-semibold text-text">{name}</span>
          <span className="block truncate">{me.tenant.name}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
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
        {isTenantAdminHint(me.tenantRoles) ? (
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
