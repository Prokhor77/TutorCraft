import { getLocale, getTranslations } from 'next-intl/server';
import { fetchPublicCourse } from '@/lib/server/public-api';
import { OG_IMAGE_SIZE } from '@/lib/seo/metadata';
import { renderOgImage } from '@/lib/seo/og-image';
import { siteUrl } from '@/lib/seo/site';

export const runtime = 'nodejs';
export const size = OG_IMAGE_SIZE;
export const contentType = 'image/png';
export const alt = 'TutorCraft';

type Params = { params: Promise<{ tenantSlug: string; courseSlug: string }> };

/** Course link preview: title, school and teacher. Replaces the pre-signed cover URL, which expires in minutes. */
export default async function Image({ params }: Params) {
  const { tenantSlug, courseSlug } = await params;
  const locale = await getLocale();
  const t = await getTranslations('seo');
  const course = await fetchPublicCourse(tenantSlug, courseSlug, locale).catch(() => null);
  return renderOgImage({
    eyebrow: course ? t('courseOgEyebrow', { school: course.tenantName }) : 'TutorCraft',
    title: course?.title ?? courseSlug,
    subtitle: course?.teacher.name
      ? t('courseOgTeacher', { name: course.teacher.name })
      : undefined,
    footer: new URL(siteUrl()).host,
  });
}
