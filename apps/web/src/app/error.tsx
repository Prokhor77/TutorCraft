'use client';
import { CloudOff, RotateCcw } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';
import { CenteredCard, PublicBlobs, StatusMessage } from '@/components/public/status-card';
import { Button } from '@/components/ui/button';

/** Error boundary for public pages (landing, storefront, auth): friendly centred card with pill actions. */
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
    <main className="relative flex min-h-dvh items-center justify-center overflow-clip bg-background px-4 py-10">
      <PublicBlobs />
      <CenteredCard className="max-w-lg">
        <StatusMessage
          icon={CloudOff}
          tone="danger"
          role="alert"
          eyebrow={t('pageEyebrow')}
          title={t('pageTitle')}
          description={t('pageDescription')}
          actions={
            <>
              <Button size="lg" onClick={reset}>
                <RotateCcw aria-hidden /> {t('retry')}
              </Button>
              <Button asChild size="lg" variant="secondary">
                <Link href="/">{t('goHome')}</Link>
              </Button>
            </>
          }
        />
      </CenteredCard>
    </main>
  );
}
