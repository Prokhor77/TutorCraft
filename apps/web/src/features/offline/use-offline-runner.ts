'use client';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useEffect, useSyncExternalStore } from 'react';
import { toast } from '@/components/ui/toast';
import { assessmentApi } from '@/lib/api/endpoints/assessment';
import { quizApi } from '@/lib/api/endpoints/quiz';
import type { QuestionResponse } from '@/lib/api/schemas/quiz';
import type { QueuedOperation } from '@/lib/offline/queue';
import {
  getOfflineQueue,
  isRetryableError,
  OPERATION_KINDS,
  type FinishAttemptPayload,
  type SaveQuizAnswerPayload,
  type SubmitAssignmentPayload,
} from '@/lib/offline/queues';
import { queryKeys } from '../query-keys';
import { describeProblem } from '../app/use-problem-toast';

const FLUSH_INTERVAL_MS = 15_000;

async function executeOperation(operation: QueuedOperation): Promise<void> {
  switch (operation.kind) {
    case OPERATION_KINDS.submitAssignment: {
      const payload = operation.payload as SubmitAssignmentPayload;
      await assessmentApi.submit(payload.itemId, operation.idempotencyKey);
      return;
    }
    case OPERATION_KINDS.saveQuizAnswer: {
      const payload = operation.payload as SaveQuizAnswerPayload;
      await quizApi.saveAnswer(payload.attemptId, payload.slot, {
        response: payload.response as QuestionResponse,
        flagged: payload.flagged,
      });
      return;
    }
    case OPERATION_KINDS.finishAttempt: {
      const payload = operation.payload as FinishAttemptPayload;
      await quizApi.finish(payload.attemptId, operation.idempotencyKey);
      return;
    }
    default:
      console.warn('[offline-queue] unknown operation kind dropped');
  }
}

/** Flushes the persistent queue on `online`, on start and periodically (AC-3, FR-QUIZ-03). */
export function useOfflineRunner(): void {
  const queryClient = useQueryClient();
  const tErrors = useTranslations('errors');
  const tOffline = useTranslations('offline');

  useEffect(() => {
    const queue = getOfflineQueue();
    const handler = async (operation: QueuedOperation) => {
      try {
        await executeOperation(operation);
        void queryClient.invalidateQueries({ queryKey: ['items'] });
        void queryClient.invalidateQueries({ queryKey: ['attempts'] });
        void queryClient.invalidateQueries({ queryKey: queryKeys.myTasks });
      } catch (error) {
        if (!isRetryableError(error))
          toast({ tone: 'error', ...describeProblem(error, tErrors, tOffline('dropped')) });
        throw error;
      }
    };
    const flush = () => {
      if (queue.list().length === 0 || !navigator.onLine) return;
      void queue.flush(handler).then((outcome) => {
        if (outcome.succeeded > 0 && outcome.pending === 0)
          toast({ tone: 'success', title: tOffline('synced') });
      });
    };
    flush();
    window.addEventListener('online', flush);
    const interval = setInterval(flush, FLUSH_INTERVAL_MS);
    return () => {
      window.removeEventListener('online', flush);
      clearInterval(interval);
    };
  }, [queryClient, tErrors, tOffline]);
}

function subscribeOnline(callback: () => void) {
  window.addEventListener('online', callback);
  window.addEventListener('offline', callback);
  return () => {
    window.removeEventListener('online', callback);
    window.removeEventListener('offline', callback);
  };
}

export function useOnlineStatus(): boolean {
  return useSyncExternalStore(
    subscribeOnline,
    () => navigator.onLine,
    () => true,
  );
}

/** Live view of pending operations with a given dedupe key prefix. */
export function usePendingOperations(dedupePrefix: string): QueuedOperation[] {
  const queue = getOfflineQueue();
  const snapshot = useSyncExternalStore(
    (callback) => queue.subscribe(callback),
    () =>
      JSON.stringify(
        queue.list().filter((operation) => operation.dedupeKey.startsWith(dedupePrefix)),
      ),
    () => '[]',
  );
  return JSON.parse(snapshot) as QueuedOperation[];
}
