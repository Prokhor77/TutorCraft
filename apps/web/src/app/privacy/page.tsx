import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { LegalDocument } from '@/components/legal/legal-document';
import { privacyContent } from '@/content/legal/privacy';
import { ROUTES } from '@/features/auth/routes';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal');
  // Inherits `unlisted` robots from the root layout: reachable and followed, but not a search landing.
  return { title: t('privacy'), alternates: { canonical: ROUTES.privacy } };
}

export default function PrivacyPage() {
  return <LegalDocument content={privacyContent} />;
}
