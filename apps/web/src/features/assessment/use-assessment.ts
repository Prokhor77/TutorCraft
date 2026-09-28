'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useCallback, useState } from 'react';
import { toast } from '@/components/ui/toast';
import {
  assessmentApi,
  type GradeSubmissionInput,
  type QueueQuery,
} from '@/lib/api/endpoints/assessment';
import { newIdempotencyKey } from '@/lib/api/idempotency';
import { getNextCursor } from '@/lib/api/pagination';
import type { BlockDoc } from '@/lib/api/schemas/blockdoc';
import type { Submission } from '@/lib/api/schemas/assessment';
import {
  getOfflineQueue,
  isRetryableError,
  OPERATION_KINDS,
  type SubmitAssignmentPayload,
} from '@/lib/offline/queues';
import { describeProblem } from '../app/use-problem-toast';
import { queryKeys } from '../query-keys';

export function useMySubmission(itemId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.mySubmission(itemId),
    queryFn: () => assessmentApi.mySubmission(itemId),
    enabled,
  });
}

export function useSaveDraft(itemId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (draft: { text?: BlockDoc; fileIds?: string[] }) =>
      assessmentApi.saveDraft(itemId, draft),
    onSuccess: (submission) => queryClient.setQueryData(queryKeys.mySubmission(itemId), submission),
    meta: { skipErrorToast: true },
  });
}

export const submitDedupeKey = (itemId: string) => `submit:${itemId}`;

/**
 * AC-3 / NFR-REL-05: the submit is persisted in the offline queue with its Idempotency-Key BEFORE the request.
 * A network failure leaves it queued; the runner retries on `online` with the same key → exactly one submission.
 */
export function useSubmitAssignment(itemId: string) {
  const queryClient = useQueryClient();
  const t = useTranslations('assignment');
  const tErrors = useTranslations('errors');
  const [isSubmitting, setSubmitting] = useState(false);

  const submit = useCallback(async (): Promise<Submission | null> => {
    const queue = getOfflineQueue();
    const operation = queue.enqueue<SubmitAssignmentPayload>({
      kind: OPERATION_KINDS.submitAssignment,
      dedupeKey: submitDedupeKey(itemId),
      payload: { itemId },
      idempotencyKey: newIdempotencyKey(),
    });
    setSubmitting(true);
    try {
      const submission = await assessmentApi.submit(itemId, operation.idempotencyKey);
      queue.remove(operation.id);
      queryClient.setQueryData(queryKeys.mySubmission(itemId), submission);
      void queryClient.invalidateQueries({ queryKey: queryKeys.myTasks });
      toast({ tone: 'success', title: t('submitted') });
      return submission;
    } catch (error) {
      if (isRetryableError(error)) {
        toast({ title: t('queuedTitle'), description: t('queuedText') });
      } else {
        queue.remove(operation.id);
        toast({ tone: 'error', ...describeProblem(error, tErrors) });
      }
      return null;
    } finally {
      setSubmitting(false);
    }
  }, [itemId, queryClient, t, tErrors]);

  return { submit, isSubmitting };
}

export function useSubmissions(itemId: string, status?: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: queryKeys.submissions(itemId, { status }),
    queryFn: ({ pageParam }) => assessmentApi.submissions(itemId, { status, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
    enabled,
  });
}

export function useSubmission(id: string | null) {
  return useQuery({
    queryKey: queryKeys.submission(id ?? 'none'),
    queryFn: () => assessmentApi.submission(id as string),
    enabled: !!id,
  });
}

export function useGradeSubmission() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: GradeSubmissionInput }) =>
      assessmentApi.grade(id, input),
    onSuccess: (submission) => {
      queryClient.setQueryData(queryKeys.submission(submission.id), submission);
      void queryClient.invalidateQueries({ queryKey: queryKeys.gradingQueueRoot });
      void queryClient.invalidateQueries({ queryKey: ['items', submission.itemId, 'submissions'] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.teaching });
    },
  });
}

export function usePublishGrades(itemId: string) {
  const t = useTranslations('assignment');
  return useMutation({
    mutationFn: () => assessmentApi.publishGrades(itemId),
    onSuccess: ({ published }) =>
      toast({ tone: 'success', title: t('publishedCount', { count: published }) }),
  });
}

export function useGrantExtension(itemId: string) {
  const t = useTranslations('assignment');
  return useMutation({
    mutationFn: (input: { userId?: string; groupId?: string; dueAt: string; closeAt?: string }) =>
      assessmentApi.extension(itemId, input),
    onSuccess: () => toast({ tone: 'success', title: t('extensionGranted') }),
  });
}

export function useGradingQueue(params: Omit<QueueQuery, 'cursor'>) {
  return useInfiniteQuery({
    queryKey: queryKeys.gradingQueue(params),
    queryFn: ({ pageParam }) => assessmentApi.queue({ ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}
