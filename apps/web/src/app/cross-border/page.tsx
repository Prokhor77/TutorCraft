import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { LegalDocument } from '@/components/legal/legal-document';
import { crossBorderContent } from '@/content/legal/cross-border';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('legal');
  return { title: t('crossBorder') };
}

export default function CrossBorderPage() {
  return <LegalDocument content={crossBorderContent} />;
}
