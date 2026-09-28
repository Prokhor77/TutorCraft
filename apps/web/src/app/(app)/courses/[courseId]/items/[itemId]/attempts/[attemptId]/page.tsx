'use client';
import { useParams } from 'next/navigation';
import { AttemptRunner } from '@/components/quiz/attempt-runner';

export default function AttemptRoute() {
  const { courseId, itemId, attemptId } = useParams<{
    courseId: string;
    itemId: string;
    attemptId: string;
  }>();
  return <AttemptRunner courseId={courseId} itemId={itemId} attemptId={attemptId} />;
}
