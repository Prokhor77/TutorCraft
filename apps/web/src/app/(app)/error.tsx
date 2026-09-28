'use client';
import { AlertTriangle, Home, RotateCcw } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { ROUTES } from '@/features/auth/routes';
import { reportClientError } from '@/lib/activity/reporter';

/** Route error inside the app shell: a Stitch white panel with a danger chip, retry and a way home. */
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
    reportClientError(error);
  }, [error]);
  return (
    <section
      role="alert"
      className="mx-auto mt-4 flex max-w-xl flex-col items-center gap-4 rounded-lg border border-card-border bg-surface px-6 py-10 text-center shadow-sm sm:mt-10 sm:px-10"
    >
      <span className="flex size-14 items-center justify-center rounded-full bg-danger-soft text-danger shadow-sm">
        <AlertTriangle className="size-6" aria-hidden />
      </span>
      <span className="rounded-full bg-danger-soft px-2.5 py-0.5 text-label-md uppercase text-danger">
        {t('pageEyebrow')}
      </span>
      <div className="flex flex-col gap-1.5">
        <h1 className="text-2xl">{t('pageTitle')}</h1>
        <p className="text-sm text-text-muted">{t('pageDescription')}</p>
      </div>
      <div className="flex flex-wrap justify-center gap-2">
        <Button onClick={reset}>
          <RotateCcw aria-hidden /> {t('retry')}
        </Button>
        <Button asChild variant="secondary">
          <Link href={ROUTES.home}>
            <Home aria-hidden /> {t('goHome')}
          </Link>
        </Button>
      </div>
    </section>
  );
}
