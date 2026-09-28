'use client';
import { useTranslations } from 'next-intl';
import { useCallback } from 'react';
import { toast } from '@/components/ui/toast';
import { isApiProblem, PROBLEM_CODES } from '@/lib/api/problem';

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

export function describeProblem(
  error: unknown,
  t: (key: string) => string,
  fallbackTitle?: string,
): { title: string; description?: string } {
  if (!isApiProblem(error)) return { title: fallbackTitle ?? t('generic') };
  if (error.code === PROBLEM_CODES.network)
    return { title: t('network'), description: t('networkHint') };
  if (error.code === PROBLEM_CODES.versionConflict)
    return { title: t('conflict'), description: t('conflictHint') };
  if (error.code === PROBLEM_CODES.invalidResponse) return { title: t('invalidResponse') };
  return { title: error.title || fallbackTitle || t('generic'), description: error.detail };
}
