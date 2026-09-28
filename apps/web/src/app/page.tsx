import {
  BellRing,
  ClipboardCheck,
  CreditCard,
  Gauge,
  ListChecks,
  PenTool,
  Smartphone,
  Timer,
} from 'lucide-react';
import type { Metadata } from 'next';
import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { PublicHeader } from '@/components/public/public-header';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ROUTES } from '@/features/auth/routes';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('landing');
  return {
    title: { absolute: t('metaTitle') },
    description: t('heroSubtitle'),
    openGraph: { title: t('metaTitle'), description: t('heroSubtitle') },
  };
}

const FEATURES = [
  { key: 'builder', icon: PenTool },
  { key: 'tasks', icon: ListChecks },
  { key: 'grading', icon: ClipboardCheck },
  { key: 'quizzes', icon: Timer },
  { key: 'sales', icon: CreditCard },
  { key: 'telegram', icon: BellRing },
  { key: 'mobile', icon: Smartphone },
  { key: 'gradebook', icon: Gauge },
] as const;

const STEPS = ['create', 'fill', 'invite', 'grade'] as const;
const PLANS = ['starter', 'pro', 'school'] as const;

/** Marketing landing for tutors: "Курс за 15 минут". */
export default async function LandingPage() {
  const t = await getTranslations('landing');
  return (
    <>
      <PublicHeader />
      <main id="main-content">
        <section className="mx-auto flex max-w-content flex-col items-center gap-6 px-page-x py-16 text-center md:py-24">
          <span className="rounded-full bg-accent/10 px-4 py-1.5 text-label-md uppercase text-primary">
            {t('badge')}
          </span>
          <h1 className="max-w-3xl text-hero-mobile font-bold md:text-4xl">{t('heroTitle')}</h1>
          <p className="max-w-2xl text-lg text-text-muted">{t('heroSubtitle')}</p>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Button asChild size="lg">
              <Link href={ROUTES.register}>{t('ctaPrimary')}</Link>
            </Button>
            <Button asChild size="lg" variant="secondary">
              <Link href={ROUTES.login}>{t('ctaSecondary')}</Link>
            </Button>
          </div>
        </section>

        <section aria-labelledby="steps-title" className="border-y border-border bg-surface">
          <div className="mx-auto max-w-content px-page-x py-12">
            <h2 id="steps-title" className="mb-8 text-center text-2xl md:text-3xl">
              {t('stepsTitle')}
            </h2>
            <ol className="grid grid-cols-1 gap-4 md:grid-cols-4">
              {STEPS.map((step, index) => (
                <li key={step} className="flex flex-col gap-2 rounded-md bg-surface-muted p-5">
                  <span className="flex size-8 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground">
                    {index + 1}
                  </span>
                  <h3 className="text-base">{t(`steps.${step}.title`)}</h3>
                  <p className="text-sm text-text-muted">{t(`steps.${step}.text`)}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section aria-labelledby="features-title" className="mx-auto max-w-content px-page-x py-16">
          <h2 id="features-title" className="mb-8 text-center text-2xl md:text-3xl">
            {t('featuresTitle')}
          </h2>
          <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {FEATURES.map(({ key, icon: Icon }) => (
              <li key={key}>
                <Card className="flex h-full flex-col gap-3 p-5">
                  <Icon className="size-6 text-primary" aria-hidden />
                  <h3 className="text-base">{t(`features.${key}.title`)}</h3>
                  <p className="text-sm text-text-muted">{t(`features.${key}.text`)}</p>
                </Card>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="pricing-title" className="border-t border-border bg-surface">
          <div className="mx-auto max-w-content px-page-x py-16">
            <h2 id="pricing-title" className="mb-2 text-center text-2xl md:text-3xl">
              {t('pricingTitle')}
            </h2>
            <p className="mb-8 text-center text-text-muted">{t('pricingSubtitle')}</p>
            <ul className="grid grid-cols-1 gap-4 md:grid-cols-3">
              {PLANS.map((plan) => (
                <li key={plan}>
                  <Card className="flex h-full flex-col gap-3 p-6">
                    <h3 className="text-lg">{t(`plans.${plan}.name`)}</h3>
                    <p className="text-3xl font-bold">{t(`plans.${plan}.price`)}</p>
                    <p className="text-sm text-text-muted">{t(`plans.${plan}.text`)}</p>
                  </Card>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="mx-auto flex max-w-content flex-col items-center gap-4 px-page-x py-16 text-center">
          <h2 className="text-2xl md:text-3xl">{t('finalTitle')}</h2>
          <Button asChild size="lg">
            <Link href={ROUTES.register}>{t('ctaPrimary')}</Link>
          </Button>
        </section>
      </main>
      <footer className="border-t border-border py-6 text-center text-sm text-text-muted">
        © TutorCraft
      </footer>
    </>
  );
}
