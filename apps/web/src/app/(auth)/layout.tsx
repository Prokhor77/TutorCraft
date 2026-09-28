import { CheckCircle2, FileQuestion, FolderTree, GraduationCap, Video } from 'lucide-react';
import { getTranslations } from 'next-intl/server';
import type { ReactNode } from 'react';
import { AuthTabs } from '@/components/auth/auth-tabs';
import { SiteFooter } from '@/components/landing/site-footer';
import { SiteHeader } from '@/components/landing/site-header';
import { LogoMark } from '@/components/layout/brand';
import { MAIN_CONTENT_ID } from '@/components/layout/skip-link';

const BRAND_FEATURES = [
  { key: 'builder', icon: FolderTree },
  { key: 'quiz', icon: FileQuestion },
  { key: 'grading', icon: CheckCircle2 },
  { key: 'media', icon: Video },
] as const;

/** Desktop brand panel: violet 3rem card with the product promise and the four real studio tools. */
async function BrandPanel() {
  const t = await getTranslations('auth');
  const tTools = await getTranslations('landing.tools');
  return (
    <aside
      aria-label={t('brandEyebrow')}
      className="relative hidden overflow-hidden rounded-xl bg-primary p-10 text-primary-foreground shadow-lg lg:flex lg:flex-col lg:gap-8 xl:p-12"
    >
      <div
        aria-hidden
        className="pointer-events-none absolute -right-20 -top-24 size-80 rounded-full bg-primary-container opacity-70 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-24 -left-16 size-72 rounded-full bg-success-accent opacity-25 blur-3xl"
      />
      <div className="relative flex flex-col gap-5">
        <span className="flex size-16 items-center justify-center rounded-md bg-surface shadow-md">
          <LogoMark className="size-11" />
        </span>
        <span className="w-fit rounded-full bg-primary-foreground/15 px-4 py-1.5 text-label-md uppercase">
          {t('brandEyebrow')}
        </span>
        <p className="font-heading text-3xl font-bold leading-tight">{t('brandTitle')}</p>
        <p className="text-base opacity-90">{t('brandSubtitle')}</p>
      </div>
      <ul className="relative grid grid-cols-2 gap-3">
        {BRAND_FEATURES.map(({ key, icon: Icon }) => (
          <li
            key={key}
            className="flex flex-col gap-3 rounded-lg bg-primary-foreground/10 p-4 ring-1 ring-primary-foreground/15"
          >
            <span className="flex size-10 items-center justify-center rounded-full bg-surface text-primary shadow-sm">
              <Icon className="size-5" aria-hidden />
            </span>
            <span className="flex flex-col gap-0.5">
              <span className="font-heading text-base font-semibold">{tTools(`${key}.title`)}</span>
              <span className="text-xs opacity-85">{tTools(`${key}.check1`)}</span>
            </span>
          </li>
        ))}
      </ul>
      <p className="relative mt-auto flex items-start gap-3 rounded-lg bg-primary-foreground/10 p-4 text-sm">
        <GraduationCap className="mt-0.5 size-5 shrink-0" aria-hidden />
        {t('brandStudentNote')}
      </p>
    </aside>
  );
}

/**
 * Stitch auth screen. Mobile: soft violet/emerald canvas, logo hero and a rounded card with «Вход · Регистрация».
 * Desktop (lg+): split layout — violet brand panel on the left, the same auth card on the right.
 */
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
        className="relative mx-auto grid w-full max-w-6xl flex-1 grid-cols-1 items-stretch gap-10 px-4 py-8 sm:py-12 lg:grid-cols-[minmax(0,1fr)_28rem] lg:px-page-x lg:py-16"
      >
        <BrandPanel />
        <div className="flex flex-col items-center gap-6 lg:justify-center">
          <div className="flex flex-col items-center gap-3 text-center lg:hidden">
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
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
