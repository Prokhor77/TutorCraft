import type { Metadata } from 'next';
import { getLocale, getTranslations } from 'next-intl/server';
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
import { JsonLd } from '@/components/seo/json-ld';
import { LANDING } from '@/content/landing';
import { localize } from '@/content/localize';
import { LOCALES } from '@/i18n/config';
import { pageMetadata } from '@/lib/seo/metadata';
import { SITE_NAME, siteUrl } from '@/lib/seo/site';
import {
  graph,
  ids,
  organizationNode,
  softwareNode,
  webPageNode,
  websiteNode,
} from '@/lib/seo/structured-data';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('seo');
  const locale = await getLocale();
  return pageMetadata({
    path: '/',
    title: t('landingTitle'),
    description: t('landingDescription'),
    locale,
  });
}

/** Organization + WebSite + the product with its real subscription plans (content/landing.ts). */
async function LandingStructuredData() {
  const t = await getTranslations('seo');
  const tMeta = await getTranslations('meta');
  const locale = await getLocale();
  const origin = siteUrl();
  const sameAs = LANDING.contacts.telegram ? [LANDING.contacts.telegram] : [];
  return (
    <JsonLd
      data={graph(
        organizationNode(origin, { name: SITE_NAME, email: LANDING.contacts.email, sameAs }),
        websiteNode(origin, {
          name: SITE_NAME,
          description: tMeta('description'),
          languages: [...LOCALES],
        }),
        webPageNode(origin, {
          url: `${origin}/`,
          name: t('landingTitle'),
          description: t('landingDescription'),
          locale,
          about: ids.product(origin),
        }),
        softwareNode(origin, {
          name: SITE_NAME,
          description: t('landingDescription'),
          locale,
          features: LANDING.pricing.features.map((feature) => localize(feature, locale)),
          plans: LANDING.pricing.plans.map((plan) => ({
            name: localize(plan.name, locale),
            price: plan.price,
            currency: LANDING.pricing.currency,
            months: plan.months,
          })),
        }),
      )}
    />
  );
}

/**
 * Public landing (Stitch «Главная»): header · hero + mockup · trust strip · 4 tools · savings calculator ·
 * student benefits · testimonials (only with real data) · pricing · final CTA · footer.
 */
export default async function LandingPage() {
  const t = await getTranslations('landing.calculator');
  return (
    <>
      <LandingStructuredData />
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
