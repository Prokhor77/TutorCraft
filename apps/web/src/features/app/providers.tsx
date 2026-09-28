'use client';
import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useState, type ReactNode } from 'react';
import { toast, Toaster } from '@/components/ui/toast';
import { TooltipProvider } from '@/components/ui/tooltip';
import { HTTP_STATUS, isApiProblem } from '@/lib/api/problem';
import { useSaveStore } from '@/stores/save-store';
import { describeProblem } from './use-problem-toast';
import { ServiceWorkerRegistrar } from './service-worker';

const STALE_TIME_MS = 30_000;
const MAX_QUERY_RETRIES = 2;
const TOOLTIP_DELAY_MS = 300;

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: { skipErrorToast?: boolean };
  }
}

function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (
    isApiProblem(error) &&
    error.status >= HTTP_STATUS.unauthorized &&
    error.status < HTTP_STATUS.serverError
  )
    return false;
  return failureCount < MAX_QUERY_RETRIES;
}

export function Providers({ children }: { children: ReactNode }) {
  const tErrors = useTranslations('errors');
  const tCommon = useTranslations('common');
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: STALE_TIME_MS,
            retry: shouldRetryQuery,
            refetchOnWindowFocus: true,
          },
        },
        mutationCache: new MutationCache({
          onSuccess: () => useSaveStore.getState().markSaved(),
          onError: (error, _variables, _context, mutation) => {
            if (mutation.meta?.skipErrorToast) return;
            toast({ tone: 'error', ...describeProblem(error, tErrors) });
          },
        }),
      }),
  );
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider delayDuration={TOOLTIP_DELAY_MS}>
        {children}
        <Toaster closeLabel={tCommon('close')} />
        <ServiceWorkerRegistrar />
      </TooltipProvider>
    </QueryClientProvider>
  );
}
