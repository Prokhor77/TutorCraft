'use client';
import { useTranslations } from 'next-intl';
import { useCallback } from 'react';
import { toast } from '@/components/ui/toast';
import { HTTP_STATUS, isApiProblem, PROBLEM_CODES, type ApiProblem } from '@/lib/api/problem';

type Translate = (key: string, values?: Record<string, string>) => string;

/** Never swallow errors: surface Problem.title (server-localized) or a generic message. */
export function useProblemToast() {
  const t = useTranslations('errors');
  return useCallback(
    (error: unknown, fallbackTitle?: string) => {
      toast({ tone: 'error', ...describeProblem(error, t, fallbackTitle) });
    },
    [t],
  );
}

/**
 * Server failures carry the activity-log request id: the user can quote it and the admin finds the request
 * (and everything the user did before it) in Администрирование → Активность.
 */
function referenceOf(error: ApiProblem, t: Translate): string | undefined {
  const failed =
    error.status >= HTTP_STATUS.serverError || error.code === PROBLEM_CODES.invalidResponse;
  return failed && error.requestId ? t('reference', { id: error.requestId }) : undefined;
}

export function describeProblem(
  error: unknown,
  t: Translate,
  fallbackTitle?: string,
): { title: string; description?: string } {
  if (!isApiProblem(error)) return { title: fallbackTitle ?? t('generic') };
  if (error.code === PROBLEM_CODES.network)
    return { title: t('network'), description: t('networkHint') };
  if (error.code === PROBLEM_CODES.versionConflict)
    return { title: t('conflict'), description: t('conflictHint') };
  if (error.code === PROBLEM_CODES.invalidResponse)
    return { title: t('invalidResponse'), description: referenceOf(error, t) };
  const description = [error.detail, referenceOf(error, t)].filter(Boolean).join(' · ');
  return {
    title: error.title || fallbackTitle || t('generic'),
    description: description || undefined,
  };
}
