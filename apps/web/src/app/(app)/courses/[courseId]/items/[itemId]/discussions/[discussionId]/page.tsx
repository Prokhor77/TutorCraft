'use client';
import { useParams } from 'next/navigation';
import { ThreadView } from '@/components/forum/thread-view';

export default function DiscussionRoute() {
  const { itemId, discussionId } = useParams<{ itemId: string; discussionId: string }>();
  return <ThreadView itemId={itemId} discussionId={discussionId} />;
}
