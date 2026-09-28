import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { SavingsCalculator } from '@/components/landing/savings-calculator';
import {
  FinalCta,
  Hero,
  LandingSection,
  Pricing,
  StudentBenefits,
  Testimonials,
  ToolsBento,
  TrustStrip,
} from '@/components/landing/sections';
import { LANDING_ANCHORS, SiteHeader } from '@/components/landing/site-header';
import { SiteFooter } from '@/components/landing/site-footer';
import { MAIN_CONTENT_ID } from '@/components/layout/skip-link';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('landing');
  return {
    title: { absolute: t('metaTitle') },
    description: t('metaDescription'),
    openGraph: { title: t('metaTitle'), description: t('metaDescription') },
  };
}

/**
 * Public landing (Stitch «Главная»): header · hero + mockup · trust strip · 4 tools · savings calculator ·
 * student benefits · testimonials (only with real data) · pricing · final CTA · footer.
 */
export default async function LandingPage() {
  const t = await getTranslations('landing.calculator');
  return (
    <>
      <SiteHeader />
      <main id={MAIN_CONTENT_ID}>
        <Hero />
        <TrustStrip />
        <ToolsBento />
        <LandingSection
          id={LANDING_ANCHORS.calculator}
          eyebrow={t('eyebrow')}
          title={t('title')}
          subtitle={t('subtitle')}
        >
          <SavingsCalculator />
        </LandingSection>
        <StudentBenefits />
        <Testimonials />
        <Pricing />
        <FinalCta />
      </main>
      <SiteFooter />
    </>
  );
}
