'use client';
import { Bell, CheckCheck, Settings } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { LoadMore } from '@/components/ui/load-more';
import { PageHeader, Panel } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import {
  useMarkNotificationsRead,
  useNotificationsFeed,
} from '@/features/notifications/use-notifications';
import { flattenPages } from '@/lib/api/pagination';
import { cn } from '@/lib/utils/cn';
import { formatDateTime, formatRelative } from '@/lib/utils/format';

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
        meta={
          feed.isSuccess ? (
            <Badge tone={unread > 0 ? 'primary' : 'neutral'} dot>
              {t('unread', { count: unread })}
            </Badge>
          ) : null
        }
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
      {feed.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void feed.refetch()}
        />
      ) : null}
      {feed.isSuccess && items.length === 0 ? (
        <EmptyState icon={Bell} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      {items.length > 0 ? (
        <Panel>
          <ul className="flex flex-col gap-2">
            {items.map((notification) => {
              const isUnread = !notification.readAt;
              const body = (
                <>
                  <span
                    className={cn(
                      'relative flex size-10 shrink-0 items-center justify-center rounded-full',
                      isUnread ? 'bg-primary text-primary-foreground' : 'bg-surface text-outline',
                    )}
                  >
                    <Bell className="size-4" aria-hidden />
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="flex items-center gap-2">
                      <span className={cn('min-w-0 text-sm', isUnread && 'font-semibold')}>
                        {notification.title}
                      </span>
                      {isUnread ? (
                        <span
                          className="size-2 shrink-0 rounded-full bg-primary"
                          aria-label={t('new')}
                        />
                      ) : null}
                    </span>
                    <span className="text-sm text-text-muted">{notification.body}</span>
                  </span>
                  <time
                    dateTime={notification.createdAt}
                    title={formatDateTime(notification.createdAt, locale)}
                    className="shrink-0 rounded-full bg-surface px-2.5 py-0.5 text-label-sm text-text-muted shadow-sm"
                  >
                    {formatRelative(notification.createdAt, locale)}
                  </time>
                </>
              );
              const className = cn(
                'flex items-start gap-3 rounded-md border px-3 py-3 text-left transition-[background-color,border-color,box-shadow] duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 sm:items-center sm:px-4',
                isUnread
                  ? 'border-card-border-hover bg-primary-soft/40 hover:bg-primary-soft/60'
                  : 'border-transparent bg-surface-muted/50 hover:bg-surface-muted',
              );
              const onOpen = () => isUnread && markRead.mutate({ ids: [notification.id] });
              return (
                <li key={notification.id}>
                  {notification.link ? (
                    <Link href={notification.link} className={className} onClick={onOpen}>
                      {body}
                    </Link>
                  ) : (
                    <button type="button" className={cn(className, 'w-full')} onClick={onOpen}>
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
        </Panel>
      ) : null}
    </>
  );
}
