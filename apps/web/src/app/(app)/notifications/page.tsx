'use client';
import { Bell, CheckCheck, Settings } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { LoadMore } from '@/components/ui/load-more';
import { PageHeader } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import {
  useMarkNotificationsRead,
  useNotificationsFeed,
} from '@/features/notifications/use-notifications';
import { flattenPages } from '@/lib/api/pagination';
import { cn } from '@/lib/utils/cn';
import { formatRelative } from '@/lib/utils/format';

/** Notification center (FR-NOTIF-01). */
export default function NotificationsPage() {
  const t = useTranslations('notifications');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const feed = useNotificationsFeed();
  const markRead = useMarkNotificationsRead();
  const items = flattenPages(feed.data?.pages);
  const unread = feed.data?.pages[0]?.unreadCount ?? 0;
  return (
    <>
      <PageHeader
        title={t('title')}
        description={t('unread', { count: unread })}
        actions={
          <>
            <Button
              variant="secondary"
              size="sm"
              disabled={unread === 0}
              onClick={() => markRead.mutate({ all: true })}
            >
              <CheckCheck aria-hidden /> {t('markAll')}
            </Button>
            <Button asChild variant="ghost" size="sm">
              <Link href={ROUTES.notificationSettings}>
                <Settings aria-hidden /> {t('settings')}
              </Link>
            </Button>
          </>
        }
      />
      {feed.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {feed.isSuccess && items.length === 0 ? (
        <EmptyState icon={Bell} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      <ul className="flex flex-col gap-2">
        {items.map((notification) => {
          const body = (
            <>
              <span className="flex items-center gap-2">
                {!notification.readAt ? (
                  <span className="size-2 shrink-0 rounded-full bg-primary" aria-label={t('new')} />
                ) : null}
                <span className="font-medium">{notification.title}</span>
              </span>
              <span className="text-sm text-text-muted">{notification.body}</span>
              <span className="text-xs text-text-muted">
                {formatRelative(notification.createdAt, locale)}
              </span>
            </>
          );
          const className = cn(
            'flex flex-col gap-1 rounded border border-border bg-surface px-4 py-3',
            !notification.readAt && 'border-primary/40 bg-primary-soft/30',
          );
          const onOpen = () => !notification.readAt && markRead.mutate({ ids: [notification.id] });
          return (
            <li key={notification.id}>
              {notification.link ? (
                <Link
                  href={notification.link}
                  className={cn(className, 'hover:bg-surface-muted')}
                  onClick={onOpen}
                >
                  {body}
                </Link>
              ) : (
                <button
                  type="button"
                  className={cn(className, 'w-full text-left')}
                  onClick={onOpen}
                >
                  {body}
                </button>
              )}
            </li>
          );
        })}
      </ul>
      <LoadMore
        hasMore={!!feed.hasNextPage}
        loading={feed.isFetchingNextPage}
        onClick={() => void feed.fetchNextPage()}
        label={tCommon('loadMore')}
      />
    </>
  );
}
