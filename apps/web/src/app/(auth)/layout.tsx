import { getTranslations } from 'next-intl/server';
import type { ReactNode } from 'react';
import { AuthTabs } from '@/components/auth/auth-tabs';
import { SiteFooter } from '@/components/landing/site-footer';
import { SiteHeader } from '@/components/landing/site-header';
import { LogoMark } from '@/components/layout/brand';
import { MAIN_CONTENT_ID } from '@/components/layout/skip-link';

/** Stitch auth screen: soft violet/emerald canvas, logo hero, rounded card with «Вход · Регистрация». */
export default async function AuthLayout({ children }: { children: ReactNode }) {
  const t = await getTranslations('auth');
  const tLanding = await getTranslations('landing');
  return (
    <div className="relative flex min-h-dvh flex-col overflow-clip bg-background">
      <div
        aria-hidden
        className="pointer-events-none absolute -right-24 top-24 size-96 rounded-full bg-success-soft opacity-70 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -left-24 top-0 size-96 rounded-full bg-primary-soft opacity-70 blur-3xl"
      />
      <SiteHeader />
      <main
        id={MAIN_CONTENT_ID}
        className="relative flex flex-1 flex-col items-center gap-6 px-4 py-8 sm:py-12"
      >
        <div className="flex flex-col items-center gap-3 text-center">
          <span className="rounded-full bg-surface p-2 shadow-md">
            <LogoMark className="size-16 rounded-full" />
          </span>
          <p className="font-heading text-3xl font-bold tracking-tight">{tLanding('product')}</p>
          <p className="max-w-sm text-sm text-text-muted">{t('heroSubtitle')}</p>
        </div>
        <div className="w-full max-w-md rounded-lg border border-card-border bg-surface p-6 shadow-md sm:p-8">
          <AuthTabs />
          {children}
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
