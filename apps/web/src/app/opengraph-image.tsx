import { getTranslations } from 'next-intl/server';
import { OG_IMAGE_SIZE } from '@/lib/seo/metadata';
import { renderOgImage } from '@/lib/seo/og-image';
import { siteUrl } from '@/lib/seo/site';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const size = OG_IMAGE_SIZE;
export const contentType = 'image/png';
export const alt = 'TutorCraft';

/** Site-wide link preview (landing, legal and auth pages). */
export default async function Image() {
  const t = await getTranslations('seo');
  return renderOgImage({
    eyebrow: t('landingOgEyebrow'),
    title: t('landingOgTitle'),
    subtitle: t('landingOgSubtitle'),
    footer: new URL(siteUrl()).host,
  });
}
