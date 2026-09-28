import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { Brand } from '@/components/layout/brand';
import { LanguageMenu, ThemeMenu } from '@/components/layout/preferences-menu';
import { Button } from '@/components/ui/button';
import { ROUTES } from '@/features/auth/routes';

export async function PublicHeader({
  brandName,
  brandHref,
}: {
  brandName?: string;
  brandHref?: string;
}) {
  const t = await getTranslations('landing');
  return (
    <header className="glass sticky top-0 z-20 border-b border-card-border">
      <div className="mx-auto flex h-header max-w-content items-center gap-2 px-page-x">
        <Brand name={brandName} href={brandHref} />
        <div className="ml-auto flex items-center gap-1">
          <LanguageMenu />
          <ThemeMenu />
          <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
            <Link href={ROUTES.login}>{t('login')}</Link>
          </Button>
          <Button asChild size="sm">
            <Link href={ROUTES.register}>{t('startFree')}</Link>
          </Button>
        </div>
      </div>
    </header>
  );
}
