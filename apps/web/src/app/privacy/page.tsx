import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { LegalDocument } from '@/components/legal/legal-document';
import { privacyContent } from '@/content/legal/privacy';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal');
  return { title: t('privacy') };
}

export default function PrivacyPage() {
  return <LegalDocument content={privacyContent} />;
}
