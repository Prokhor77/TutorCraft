import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { LegalDocument } from '@/components/legal/legal-document';
import { offerContent } from '@/content/legal/offer';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal');
  return { title: t('offer') };
}

export default function OfferPage() {
  return <LegalDocument content={offerContent} />;
}
