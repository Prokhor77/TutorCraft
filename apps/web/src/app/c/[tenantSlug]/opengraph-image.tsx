import { getLocale, getTranslations } from 'next-intl/server';
import { fetchCatalog } from '@/lib/server/public-api';
import { OG_IMAGE_SIZE } from '@/lib/seo/metadata';
import { renderOgImage } from '@/lib/seo/og-image';
import { siteUrl } from '@/lib/seo/site';

export const runtime = 'nodejs';
export const size = OG_IMAGE_SIZE;
export const contentType = 'image/png';
export const alt = 'TutorCraft';

/** Storefront link preview: school name and the number of open courses. */
export default async function Image({ params }: { params: Promise<{ tenantSlug: string }> }) {
  const { tenantSlug } = await params;
  const locale = await getLocale();
  const t = await getTranslations('seo');
  const tStorefront = await getTranslations('storefront');
  const courses = (await fetchCatalog(tenantSlug, locale).catch(() => null)) ?? [];
  return renderOgImage({
    eyebrow: tStorefront('catalogEyebrow'),
    title: courses[0]?.tenantName ?? tenantSlug,
    subtitle: courses.length > 0 ? t('catalogOgSubtitle', { count: courses.length }) : undefined,
    footer: new URL(siteUrl()).host,
  });
}
