'use client';
import { Bell, CheckCheck } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { CountBadge } from '@/components/ui/badge';
import { Popover, PopoverClose, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { ROUTES } from '@/features/auth/routes';
import {
  useMarkNotificationsRead,
  useNotificationsFeed,
} from '@/features/notifications/use-notifications';
import { flattenPages } from '@/lib/api/pagination';
import { cn } from '@/lib/utils/cn';
import { formatRelative } from '@/lib/utils/format';

const DROPDOWN_LIMIT = 6;

export function NotificationsBell() {
  const t = useTranslations('notifications');
  const locale = useLocale();
  const router = useRouter();
  const feed = useNotificationsFeed();
  const markRead = useMarkNotificationsRead();
  const unread = feed.data?.pages[0]?.unreadCount ?? 0;
  const items = flattenPages(feed.data?.pages).slice(0, DROPDOWN_LIMIT);

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="relative"
          aria-label={t('bellLabel', { count: unread })}
        >
          <Bell aria-hidden />
          <CountBadge
            count={unread}
            label={t('unread', { count: unread })}
            className="absolute -right-0.5 -top-0.5"
          />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[min(24rem,calc(100vw-2rem))] p-0">
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <h2 className="text-sm font-semibold">{t('title')}</h2>
          <Button
            variant="ghost"
            size="sm"
            disabled={unread === 0}
            onClick={() => markRead.mutate({ all: true })}
          >
            <CheckCheck aria-hidden /> {t('markAll')}
          </Button>
        </div>
        <ul className="max-h-96 overflow-y-auto">
          {items.length === 0 ? (
            <li className="px-4 py-8 text-center text-sm text-text-muted">{t('emptyShort')}</li>
          ) : null}
          {items.map((notification) => (
            <li key={notification.id}>
              <PopoverClose asChild>
                <button
                  type="button"
                  className={cn(
                    'flex w-full flex-col gap-0.5 border-b border-border px-4 py-3 text-left last:border-0 hover:bg-surface-muted',
                    !notification.readAt && 'bg-primary-soft/40',
                  )}
                  onClick={() => {
                    if (!notification.readAt) markRead.mutate({ ids: [notification.id] });
                    if (notification.link) router.push(notification.link);
                  }}
                >
                  <span className="text-sm font-medium">{notification.title}</span>
                  <span className="line-clamp-2 text-sm text-text-muted">{notification.body}</span>
                  <span className="text-xs text-text-muted">
                    {formatRelative(notification.createdAt, locale)}
                  </span>
                </button>
              </PopoverClose>
            </li>
          ))}
        </ul>
        <div className="border-t border-border p-2">
          <PopoverClose asChild>
            <Link
              href={ROUTES.notifications}
              className="block rounded-sm px-2 py-1.5 text-center text-sm font-medium text-primary hover:bg-primary-soft"
            >
              {t('viewAll')}
            </Link>
          </PopoverClose>
        </div>
      </PopoverContent>
    </Popover>
  );
}
