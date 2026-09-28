import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { Button } from '@/components/ui/button';

export default async function NotFound() {
  const t = await getTranslations('errors');
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="text-4xl font-bold text-primary">404</p>
      <h1 className="text-2xl">{t('notFoundTitle')}</h1>
      <p className="max-w-md text-text-muted">{t('notFoundDescription')}</p>
      <Button asChild>
        <Link href="/">{t('goHome')}</Link>
      </Button>
    </main>
  );
}
