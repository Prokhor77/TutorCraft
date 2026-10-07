import 'server-only';
import type { Metadata } from 'next';
import { indexingAllowed, normalizePath, SITE_NAME } from './site';

/**
 * Robots presets (one vocabulary for every page):
 * - `public`   — indexable page with full snippets and large image previews;
 * - `unlisted` — keep out of the index but let crawlers follow links (legal pages, auth forms, empty catalogs);
 * - `private`  — keep out and do not follow (authenticated app, invite tokens).
 * When indexing is switched off for the whole deployment (see `indexingAllowed`), every preset is `private`.
 */
export type RobotsPreset = 'public' | 'unlisted' | 'private';

export function robots(preset: RobotsPreset): NonNullable<Metadata['robots']> {
  const effective = indexingAllowed() ? preset : 'private';
  if (effective === 'public') {
    return {
      index: true,
      follow: true,
      'max-image-preview': 'large',
      'max-snippet': -1,
      'max-video-preview': -1,
    };
  }
  return { index: false, follow: effective === 'unlisted' };
}

/** Open Graph image size used by every generated `opengraph-image` route. */
export const OG_IMAGE_SIZE = { width: 1200, height: 630 } as const;
export const DEFAULT_OG_IMAGE_PATH = '/opengraph-image';

const OG_LOCALES: Record<string, string> = { ru: 'ru_RU', en: 'en_US', uz: 'uz_UZ' };

/** `og:locale` (language_TERRITORY); unknown UI languages fall back to Russian, the default locale. */
export function ogLocale(locale: string): string {
  return OG_LOCALES[locale.slice(0, 2)] ?? 'ru_RU';
}

export type PageSeo = {
  /** Site path of the page, without query; becomes the canonical URL. */
  path: string;
  /** Full title shown in search results and link previews (≤ 60 characters, see seo.test.ts). */
  title: string;
  description: string;
  locale: string;
  robots?: RobotsPreset;
  /** Path of the 1200×630 preview image route; defaults to the site-wide one. */
  imagePath?: string;
  imageAlt?: string;
  ogType?: 'website' | 'article';
  /** Overrides `og:site_name`, e.g. with the school name on storefront pages. */
  siteName?: string;
};

/**
 * Metadata for an indexable public page: absolute title, canonical, Open Graph and Twitter card.
 * `metadataBase` (root layout) turns the relative paths into absolute URLs.
 */
export function pageMetadata(seo: PageSeo): Metadata {
  const canonical = normalizePath(seo.path);
  const image = {
    url: seo.imagePath ?? DEFAULT_OG_IMAGE_PATH,
    ...OG_IMAGE_SIZE,
    alt: seo.imageAlt ?? seo.title,
  };
  return {
    title: { absolute: seo.title },
    description: seo.description,
    alternates: { canonical },
    robots: robots(seo.robots ?? 'public'),
    openGraph: {
      type: seo.ogType ?? 'website',
      url: canonical,
      siteName: seo.siteName ?? SITE_NAME,
      locale: ogLocale(seo.locale),
      title: seo.title,
      description: seo.description,
      images: [image],
    },
    twitter: {
      card: 'summary_large_image',
      title: seo.title,
      description: seo.description,
      images: [image],
    },
  };
}

const ELLIPSIS = '…';

/** Plain-text excerpt for meta descriptions: collapses whitespace and cuts on a word boundary. */
export function excerpt(text: string, max = 160): string {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (clean.length <= max) return clean;
  const cut = clean.slice(0, max - ELLIPSIS.length);
  const lastSpace = cut.lastIndexOf(' ');
  return `${(lastSpace > max / 2 ? cut.slice(0, lastSpace) : cut).replace(/[\s,.;:—-]+$/, '')}${ELLIPSIS}`;
}
