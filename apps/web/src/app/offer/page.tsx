import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { LegalDocument } from '@/components/legal/legal-document';
import { offerContent } from '@/content/legal/offer';
import { ROUTES } from '@/features/auth/routes';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal');
  // Inherits `unlisted` robots from the root layout: reachable and followed, but not a search landing.
  return { title: t('offer'), alternates: { canonical: ROUTES.offer } };
}

export default function OfferPage() {
  return <LegalDocument content={offerContent} />;
}
