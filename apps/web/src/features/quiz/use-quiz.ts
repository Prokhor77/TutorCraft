'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { quizApi, type QuizOverrideInput } from '@/lib/api/endpoints/quiz';
import { newIdempotencyKey } from '@/lib/api/idempotency';
import { getNextCursor } from '@/lib/api/pagination';
import { hasProblemCode, PROBLEM_CODES } from '@/lib/api/problem';
import type { Attempt, AttemptResult, QuestionResponse, QuizSlot } from '@/lib/api/schemas/quiz';
import {
  getOfflineQueue,
  isRetryableError,
  OPERATION_KINDS,
  type SaveQuizAnswerPayload,
} from '@/lib/offline/queues';
import { createServerClock, remainingMs, type ServerClock } from '@/lib/utils/timer';
import { MS_PER_SECOND } from '@/lib/utils/time';
import { queryKeys } from '../query-keys';

export function useQuizSlots(itemId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.quizSlots(itemId),
    queryFn: () => quizApi.slots(itemId),
    enabled,
  });
}

export function useSaveSlots(itemId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (slots: QuizSlot[]) => quizApi.saveSlots(itemId, slots),
    onSuccess: (layout) => queryClient.setQueryData(queryKeys.quizSlots(itemId), layout),
  });
}

export function useStartAttempt(itemId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => quizApi.startAttempt(itemId),
    onSuccess: (attempt) => queryClient.setQueryData(queryKeys.attempt(attempt.id), attempt),
  });
}

export function useAttempt(attemptId: string) {
  return useQuery({
    queryKey: queryKeys.attempt(attemptId),
    queryFn: () => quizApi.attempt(attemptId),
    enabled: !!attemptId,
    refetchOnWindowFocus: false,
    staleTime: Infinity,
  });
}

export function useAttemptResult(attemptId: string) {
  return useQuery({
    queryKey: queryKeys.attemptResult(attemptId),
    queryFn: () => quizApi.result(attemptId),
  });
}

export function useQuizAttempts(itemId: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: queryKeys.quizAttempts(itemId),
    queryFn: ({ pageParam }) => quizApi.attempts(itemId, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
    enabled,
  });
}

export function useRegrade(itemId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => quizApi.regrade(itemId),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: queryKeys.quizAttempts(itemId) }),
  });
}

export function useQuizOverride(itemId: string) {
  return useMutation({ mutationFn: (input: QuizOverrideInput) => quizApi.override(itemId, input) });
}

export type SlotSaveState = 'saving' | 'saved' | 'queued' | 'error';
const answerDedupeKey = (attemptId: string, slot: number) => `answer:${attemptId}:${slot}`;
export const TEXT_ANSWER_DEBOUNCE_MS = 800;

/**
 * FR-QUIZ-03: every answer is saved on the server; failures go to the persistent retry queue
 * (latest response per slot wins). `onExpired` fires on 409 quiz.time_expired.
 */
export function useAnswerSaver(attemptId: string, onExpired: () => void) {
  const [states, setStates] = useState<Record<number, SlotSaveState>>({});
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());
  const expiredRef = useRef(onExpired);
  expiredRef.current = onExpired;

  const send = useCallback(
    async (slot: number, response: QuestionResponse, flagged?: boolean) => {
      const queue = getOfflineQueue();
      const operation = queue.enqueue<SaveQuizAnswerPayload>({
        kind: OPERATION_KINDS.saveQuizAnswer,
        dedupeKey: answerDedupeKey(attemptId, slot),
        payload: { attemptId, slot, response, flagged },
        idempotencyKey: newIdempotencyKey(),
      });
      setStates((current) => ({ ...current, [slot]: 'saving' }));
      try {
        await quizApi.saveAnswer(attemptId, slot, { response, flagged });
        const latest = queue.find(operation.dedupeKey);
        if (latest && JSON.stringify(latest.payload) === JSON.stringify(operation.payload))
          queue.remove(latest.id);
        setStates((current) => ({ ...current, [slot]: 'saved' }));
      } catch (error) {
        if (hasProblemCode(error, PROBLEM_CODES.quizTimeExpired)) {
          queue.remove(operation.id);
          expiredRef.current();
          return;
        }
        if (!isRetryableError(error)) queue.remove(operation.id);
        setStates((current) => ({
          ...current,
          [slot]: isRetryableError(error) ? 'queued' : 'error',
        }));
      }
    },
    [attemptId],
  );

  const save = useCallback(
    (
      slot: number,
      response: QuestionResponse,
      options: { flagged?: boolean; debounce?: boolean } = {},
    ) => {
      clearTimeout(timers.current.get(slot));
      if (!options.debounce) return void send(slot, response, options.flagged);
      setStates((current) => ({ ...current, [slot]: 'saving' }));
      timers.current.set(
        slot,
        setTimeout(() => void send(slot, response, options.flagged), TEXT_ANSWER_DEBOUNCE_MS),
      );
    },
    [send],
  );

  /** Sends debounced answers immediately and waits for queued ones (before finishing). */
  const flushAll = useCallback(async () => {
    timers.current.forEach((timer) => clearTimeout(timer));
    timers.current.clear();
    const pending = getOfflineQueue()
      .list(OPERATION_KINDS.saveQuizAnswer)
      .filter((operation) => (operation.payload as SaveQuizAnswerPayload).attemptId === attemptId);
    for (const operation of pending) {
      const payload = operation.payload as SaveQuizAnswerPayload;
      await send(payload.slot, payload.response as QuestionResponse, payload.flagged);
    }
  }, [attemptId, send]);

  useEffect(() => {
    const currentTimers = timers.current;
    return () => currentTimers.forEach((timer) => clearTimeout(timer));
  }, []);

  return { save, flushAll, states };
}

const FINISH_KEY_PREFIX = 'tc:finish-key:';

/** POST /attempts/{id}/finish with a stable Idempotency-Key per attempt (API-05). */
export function useFinishAttempt(attemptId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => {
      const storageKey = FINISH_KEY_PREFIX + attemptId;
      const key = sessionStorage.getItem(storageKey) ?? newIdempotencyKey();
      sessionStorage.setItem(storageKey, key);
      return quizApi.finish(attemptId, key);
    },
    onSuccess: (result: AttemptResult) => {
      queryClient.setQueryData(queryKeys.attemptResult(attemptId), result);
      void queryClient.invalidateQueries({ queryKey: queryKeys.myTasks });
    },
  });
}

const TIMER_TICK_MS = MS_PER_SECOND / 2;

/** Server-synced countdown (AC-4): offset measured from Attempt.serverNow at fetch time. */
export function useAttemptTimer(attempt: Attempt | undefined, fetchedAt: number): number | null {
  const [clock, setClock] = useState<ServerClock | null>(null);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (attempt) setClock(createServerClock(attempt.serverNow, fetchedAt));
  }, [attempt, fetchedAt]);
  useEffect(() => {
    if (!attempt?.timeDue) return;
    const timer = setInterval(() => setNow(Date.now()), TIMER_TICK_MS);
    return () => clearInterval(timer);
  }, [attempt?.timeDue]);
  if (!attempt || !clock) return null;
  return remainingMs(attempt.timeDue, clock, now);
}
