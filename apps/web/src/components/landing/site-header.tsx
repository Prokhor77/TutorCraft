import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { Brand } from '@/components/layout/brand';
import { LanguageMenu, ThemeMenu } from '@/components/layout/preferences-menu';
import { SkipLink } from '@/components/layout/skip-link';
import { Button } from '@/components/ui/button';
import { LANDING } from '@/content/landing';
import { ROUTES } from '@/features/auth/routes';

/** Landing section anchors; absolute (`/#…`) so they also work from other public pages. */
export const LANDING_ANCHORS = {
  features: 'features',
  benefits: 'benefits',
  calculator: 'calculator',
  pricing: 'pricing',
  testimonials: 'testimonials',
  demo: 'demo',
} as const;

/**
 * Public site header (landing, auth, catalog, course landing, checkout, invites): glass bar with the logo,
 * pill anchors to landing sections (xl+), «Войти в кабинет» and «Попробовать бесплатно».
 * The «Отзывы» anchor exists only when real testimonials are configured.
 */
export async function SiteHeader({
  brandName,
  brandHref = '/',
}: {
  brandName?: string;
  brandHref?: string;
}) {
  const t = await getTranslations('landing');
  const tNav = await getTranslations('nav');
  const anchors = [
    { id: LANDING_ANCHORS.features, label: t('nav.features') },
    { id: LANDING_ANCHORS.benefits, label: t('nav.benefits') },
    { id: LANDING_ANCHORS.pricing, label: t('nav.pricing') },
    ...(LANDING.testimonials.length > 0
      ? [{ id: LANDING_ANCHORS.testimonials, label: t('nav.testimonials') }]
      : []),
  ];
  return (
    <>
      <SkipLink label={tNav('skipToContent')} />
      <header className="glass sticky top-0 z-30 border-b border-card-border shadow-sm">
        <div className="mx-auto flex h-header-public max-w-content items-center gap-3 px-page-x">
          <Brand name={brandName ?? t('product')} href={brandHref} className="min-w-0" />
          <nav aria-label={t('nav.label')} className="mx-auto hidden xl:block">
            <ul className="flex items-center gap-1 rounded-full bg-surface-muted p-1">
              {anchors.map((anchor) => (
                <li key={anchor.id}>
                  <Link
                    href={`/#${anchor.id}`}
                    className="flex h-9 items-center rounded-full px-4 text-label-lg text-text-muted transition-colors duration-fast hover:bg-surface hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
                  >
                    {anchor.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
          <div className="ml-auto flex shrink-0 items-center gap-1 xl:ml-0">
            <LanguageMenu />
            <ThemeMenu />
            <Button asChild variant="secondary" size="sm" className="hidden md:inline-flex">
              <Link href={ROUTES.login}>{t('nav.login')}</Link>
            </Button>
            <Button asChild variant="secondary" size="sm" className="md:hidden">
              <Link href={ROUTES.login}>{t('nav.loginShort')}</Link>
            </Button>
            <Button asChild size="sm" className="hidden sm:inline-flex">
              <Link href={ROUTES.register}>{t('nav.tryFree')}</Link>
            </Button>
          </div>
        </div>
      </header>
    </>
  );
}
