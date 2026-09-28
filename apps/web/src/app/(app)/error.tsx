'use client';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';
import { ErrorState } from '@/components/ui/error-state';

export default function AppError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations('errors');
  useEffect(() => {
    console.error('[ui] route error', error.name, error.digest ?? '');
  }, [error]);
  return (
    <ErrorState
      title={t('pageTitle')}
      description={t('pageDescription')}
      retryLabel={t('retry')}
      onRetry={reset}
    />
  );
}
