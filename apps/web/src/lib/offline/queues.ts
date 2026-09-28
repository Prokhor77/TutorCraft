import { isApiProblem } from '@/lib/api/problem';
import { localStorageQueueStorage, OfflineQueue } from './queue';

export const QUEUE_STORAGE_KEY = 'tc:offline-queue:v1';

export const OPERATION_KINDS = {
  submitAssignment: 'assignment.submit',
  saveQuizAnswer: 'quiz.answer',
  finishAttempt: 'quiz.finish',
} as const;

export type SubmitAssignmentPayload = { itemId: string };
export type SaveQuizAnswerPayload = {
  attemptId: string;
  slot: number;
  response: unknown;
  flagged?: boolean;
};
export type FinishAttemptPayload = { attemptId: string };

export function isRetryableError(error: unknown): boolean {
  if (!isApiProblem(error)) return true;
  return error.isTransient;
}

let sharedQueue: OfflineQueue | null = null;

/** App-wide queue; the runner (features/offline) flushes it on `online` and on an interval. */
export function getOfflineQueue(): OfflineQueue {
  sharedQueue ??= new OfflineQueue(localStorageQueueStorage(QUEUE_STORAGE_KEY), {
    isRetryable: isRetryableError,
  });
  return sharedQueue;
}
