'use client';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';
import { ErrorState } from '@/components/ui/error-state';

/** Error boundary for public pages (landing, storefront, auth). */
export default function RootError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations('errors');
  useEffect(() => {
    console.error('[ui] page error', error.name, error.digest ?? '');
  }, [error]);
  return (
    <main className="mx-auto flex min-h-dvh max-w-lg items-center px-4">
      <ErrorState
        title={t('pageTitle')}
        description={t('pageDescription')}
        retryLabel={t('retry')}
        onRetry={reset}
      />
    </main>
  );
}
