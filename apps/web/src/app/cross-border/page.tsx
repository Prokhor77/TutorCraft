import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { LegalDocument } from '@/components/legal/legal-document';
import { crossBorderContent } from '@/content/legal/cross-border';
import { ROUTES } from '@/features/auth/routes';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal');
  // Inherits `unlisted` robots from the root layout: reachable and followed, but not a search landing.
  return { title: t('crossBorder'), alternates: { canonical: ROUTES.crossBorder } };
}

export default function CrossBorderPage() {
  return <LegalDocument content={crossBorderContent} />;
}
