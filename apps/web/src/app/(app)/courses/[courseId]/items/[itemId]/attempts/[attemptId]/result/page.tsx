'use client';
import { useParams } from 'next/navigation';
import { AttemptResultView } from '@/components/quiz/attempt-result';

export default function AttemptResultRoute() {
  const { courseId, itemId, attemptId } = useParams<{
    courseId: string;
    itemId: string;
    attemptId: string;
  }>();
  return <AttemptResultView courseId={courseId} itemId={itemId} attemptId={attemptId} />;
}
