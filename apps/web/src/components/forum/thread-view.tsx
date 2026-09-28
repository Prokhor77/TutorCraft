'use client';
import { Bell, BellOff, Lock, Pencil, Pin, Reply, Trash2, Unlock } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, useMemo, useState } from 'react';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { Avatar } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import {
  MAX_THREAD_DEPTH,
  replyParentId,
  useForumMutations,
  useThread,
} from '@/features/forum/use-forum';
import { PERMISSIONS } from '@/lib/access/permissions';
import type { Post } from '@/lib/api/schemas/forum';
import { cn } from '@/lib/utils/cn';
import { formatRelative } from '@/lib/utils/format';
import { PostComposer } from './post-composer';

type PostActions = {
  locked: boolean;
  canPost: boolean;
  parentOf: (id: string) => string | null;
  onReply: (parentId: string, body: Post['body']) => Promise<unknown>;
  onEdit: (id: string, body: Post['body']) => Promise<unknown>;
  onDelete: (id: string) => void;
};

function PostNode({ post, depth, actions }: { post: Post; depth: number; actions: PostActions }) {
  const t = useTranslations('forum');
  const locale = useLocale();
  const [mode, setMode] = useState<'view' | 'reply' | 'edit'>('view');
  return (
    <li className={cn('flex flex-col gap-2', depth > 1 && 'border-l-2 border-border pl-3 sm:pl-5')}>
      <article
        className="flex flex-col gap-2 rounded-md bg-surface p-3"
        aria-label={t('postBy', { name: post.authorName })}
      >
        <header className="flex items-center gap-2">
          <Avatar name={post.authorName} size="sm" />
          <span className="text-sm font-medium">{post.authorName}</span>
          <span className="text-xs text-text-muted">
            {formatRelative(post.createdAt, locale)}
            {post.editedAt ? ` · ${t('edited')}` : ''}
          </span>
        </header>
        {post.hidden ? (
          <p className="text-sm italic text-text-muted">{t('hiddenPost')}</p>
        ) : mode === 'edit' ? null : (
          <BlockRenderer doc={post.body} />
        )}
        {mode === 'edit' ? (
          <PostComposer
            label={t('editPost')}
            submitLabel={t('save')}
            initial={post.body}
            onCancel={() => setMode('view')}
            onSubmit={(body) => actions.onEdit(post.id, body).then(() => setMode('view'))}
          />
        ) : null}
        <div className="flex flex-wrap gap-1">
          {actions.canPost && !actions.locked ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setMode(mode === 'reply' ? 'view' : 'reply')}
            >
              <Reply aria-hidden /> {t('reply')}
            </Button>
          ) : null}
          {post.canEdit ? (
            <Button variant="ghost" size="sm" onClick={() => setMode('edit')}>
              <Pencil aria-hidden /> {t('edit')}
            </Button>
          ) : null}
          {post.canDelete ? (
            <Button variant="ghost" size="sm" onClick={() => actions.onDelete(post.id)}>
              <Trash2 aria-hidden /> {t('delete')}
            </Button>
          ) : null}
        </div>
        {mode === 'reply' ? (
          <PostComposer
            label={t('replyTo', { name: post.authorName })}
            submitLabel={t('reply')}
            onCancel={() => setMode('view')}
            onSubmit={(body) =>
              actions
                .onReply(replyParentId(post, depth, actions.parentOf), body)
                .then(() => setMode('view'))
            }
          />
        ) : null}
      </article>
      {post.children.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {post.children.map((child) => (
            <PostNode
              key={child.id}
              post={child}
              depth={Math.min(depth + 1, MAX_THREAD_DEPTH)}
              actions={actions}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
}

/** Discussion thread (tree ≤ 3 levels), reply, edit window, pin/lock/subscribe (FR-FORUM-01..04). */
export function ThreadView({ itemId, discussionId }: { itemId: string; discussionId: string }) {
  const t = useTranslations('forum');
  const tCommon = useTranslations('common');
  const { course, can } = useCourseContext();
  const thread = useThread(discussionId);
  const mutations = useForumMutations(itemId, discussionId);
  const { markRead } = mutations;
  const parents = useMemo(() => {
    const map = new Map<string, string | null>();
    const walk = (posts: Post[]) => {
      for (const post of posts) {
        map.set(post.id, post.parentId);
        walk(post.children);
      }
    };
    walk(thread.data?.posts ?? []);
    return map;
  }, [thread.data]);

  useEffect(() => {
    if (thread.data && thread.data.discussion.unreadCount > 0) markRead.mutate();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mark as read once per load
  }, [thread.data?.discussion.id]);

  if (thread.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (thread.isError || !thread.data)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void thread.refetch()}
      />
    );
  const { discussion, posts } = thread.data;
  const moderator = can(PERMISSIONS.forumModerate);
  const actions: PostActions = {
    locked: discussion.locked,
    canPost: can(PERMISSIONS.forumPost),
    parentOf: (id) => parents.get(id) ?? null,
    onReply: (parentId, body) => mutations.reply.mutateAsync({ parentId, body }),
    onEdit: (id, body) => mutations.edit.mutateAsync({ id, body }),
    onDelete: (id) => mutations.remove.mutate(id),
  };

  return (
    <div className="flex flex-col gap-4">
      <Link href={ROUTES.item(course.id, itemId)} className="text-sm text-primary hover:underline">
        ← {t('backToForum')}
      </Link>
      <header className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1 className="text-2xl">{discussion.title}</h1>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={() =>
              mutations.toggle.mutate({ toggle: 'subscribe', enabled: !discussion.subscribed })
            }
          >
            {discussion.subscribed ? <BellOff aria-hidden /> : <Bell aria-hidden />}{' '}
            {discussion.subscribed ? t('unsubscribe') : t('subscribe')}
          </Button>
          {moderator ? (
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={() =>
                  mutations.toggle.mutate({ toggle: 'pin', enabled: !discussion.pinned })
                }
              >
                <Pin aria-hidden /> {discussion.pinned ? t('unpin') : t('pin')}
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() =>
                  mutations.toggle.mutate({ toggle: 'lock', enabled: !discussion.locked })
                }
              >
                {discussion.locked ? <Unlock aria-hidden /> : <Lock aria-hidden />}{' '}
                {discussion.locked ? t('unlock') : t('lock')}
              </Button>
            </>
          ) : null}
        </div>
      </header>
      <ul className="flex flex-col gap-3">
        {posts.map((post) => (
          <PostNode key={post.id} post={post} depth={1} actions={actions} />
        ))}
      </ul>
      {discussion.locked ? (
        <p className="flex items-center gap-2 text-sm text-text-muted">
          <Lock className="size-4" aria-hidden /> {t('lockedNotice')}
        </p>
      ) : actions.canPost ? (
        <PostComposer
          label={t('yourReply')}
          submitLabel={t('reply')}
          onSubmit={(body) => mutations.reply.mutateAsync({ parentId: null, body })}
        />
      ) : null}
    </div>
  );
}
