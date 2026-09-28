'use client';
import { Lock, MessagesSquare, Pin, Plus } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { Alert } from '@/components/ui/alert';
import { Badge, CountBadge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { Input } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import { useDiscussions, useForumMutations } from '@/features/forum/use-forum';
import { PERMISSIONS } from '@/lib/access/permissions';
import { flattenPages } from '@/lib/api/pagination';
import type { ItemDetail } from '@/lib/api/schemas/courses';
import { formatRelative } from '@/lib/utils/format';
import { PostComposer } from './post-composer';

/** Forum item: discussions list + new topic (FR-FORUM-01..03). */
export function ForumView({ item }: { item: ItemDetail }) {
  const t = useTranslations('forum');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { can } = useCourseContext();
  const discussions = useDiscussions(item.id);
  const { createDiscussion } = useForumMutations(item.id);
  const [composing, setComposing] = useState(false);
  const [title, setTitle] = useState('');
  const settings = item.settings.kind === 'forum' ? item.settings : null;
  const canStart =
    settings?.forumType === 'announcements'
      ? can(PERMISSIONS.forumAnnounce)
      : can(PERMISSIONS.forumPost);
  const rows = flattenPages(discussions.data?.pages);

  return (
    <div className="flex flex-col gap-4">
      {settings?.forumType === 'qa' ? <Alert tone="info" title={t('qaNotice')} /> : null}
      {settings?.forumType === 'announcements' ? (
        <Alert tone="info" title={t('announcementsNotice')} />
      ) : null}
      {canStart && !composing ? (
        <Button className="self-start" onClick={() => setComposing(true)}>
          <Plus aria-hidden /> {t('newTopic')}
        </Button>
      ) : null}
      {composing ? (
        <div className="flex flex-col gap-2">
          <Input
            aria-label={t('topicTitle')}
            placeholder={t('topicTitle')}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            autoFocus
          />
          <PostComposer
            label={t('topicBody')}
            submitLabel={t('publish')}
            onCancel={() => setComposing(false)}
            onSubmit={async (body) => {
              if (!title.trim()) return;
              await createDiscussion.mutateAsync({ title: title.trim(), body });
              setTitle('');
              setComposing(false);
            }}
          />
        </div>
      ) : null}
      {discussions.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {discussions.isSuccess && rows.length === 0 ? (
        <EmptyState
          icon={MessagesSquare}
          title={t('emptyTitle')}
          description={canStart ? t('emptyTeacher') : t('emptyStudent')}
        />
      ) : null}
      <ul className="flex flex-col gap-2">
        {rows.map((discussion) => (
          <li key={discussion.id}>
            <Link
              href={ROUTES.discussion(item.courseId, item.id, discussion.id)}
              className="flex items-center gap-3 rounded border border-card-border bg-surface px-4 py-3 shadow-sm hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
            >
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="flex items-center gap-2">
                  {discussion.pinned ? (
                    <Pin className="size-4 text-primary" aria-label={t('pinned')} />
                  ) : null}
                  {discussion.locked ? (
                    <Lock className="size-4 text-text-muted" aria-label={t('locked')} />
                  ) : null}
                  <span className="truncate font-medium">{discussion.title}</span>
                </span>
                <span className="text-xs text-text-muted">
                  {discussion.authorName} · {t('replies', { count: discussion.replyCount })} ·{' '}
                  {formatRelative(discussion.lastPostAt, locale)}
                </span>
              </span>
              {discussion.unreadCount > 0 ? (
                <CountBadge
                  count={discussion.unreadCount}
                  label={t('unread', { count: discussion.unreadCount })}
                />
              ) : null}
              {discussion.subscribed ? <Badge tone="info">{t('subscribed')}</Badge> : null}
            </Link>
          </li>
        ))}
      </ul>
      <LoadMore
        hasMore={!!discussions.hasNextPage}
        loading={discussions.isFetchingNextPage}
        onClick={() => void discussions.fetchNextPage()}
        label={tCommon('loadMore')}
      />
    </div>
  );
}
