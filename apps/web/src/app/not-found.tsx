import { Compass } from 'lucide-react';
import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { Brand } from '@/components/layout/brand';
import { CenteredCard, PublicBlobs, StatusMessage } from '@/components/public/status-card';
import { Button } from '@/components/ui/button';
import { ROUTES } from '@/features/auth/routes';

/** Friendly 404: soft canvas, logo, centred 2rem card with an illustration icon and pill actions. */
export default async function NotFound() {
  const t = await getTranslations('errors');
  const tLanding = await getTranslations('landing');
  return (
    <div className="relative flex min-h-dvh flex-col items-center overflow-clip bg-background px-4 py-8">
      <PublicBlobs />
      <Brand name={tLanding('product')} className="relative" />
      <main className="relative flex w-full flex-1 items-center justify-center py-10">
        <CenteredCard className="max-w-lg">
          <StatusMessage
            icon={Compass}
            eyebrow={t('notFoundEyebrow')}
            title={t('notFoundTitle')}
            description={t('notFoundDescription')}
            actions={
              <>
                <Button asChild size="lg">
                  <Link href="/">{t('goHome')}</Link>
                </Button>
                <Button asChild size="lg" variant="secondary">
                  <Link href={ROUTES.home}>{t('goToCabinet')}</Link>
                </Button>
              </>
            }
          />
        </CenteredCard>
      </main>
    </div>
  );
}
