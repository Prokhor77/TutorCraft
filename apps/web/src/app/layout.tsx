import type { Metadata, Viewport } from 'next';
import { cookies } from 'next/headers';
import { NextIntlClientProvider } from 'next-intl';
import { getLocale, getMessages, getTranslations } from 'next-intl/server';
import type { ReactNode } from 'react';
import { Providers } from '@/features/app/providers';
import { DEFAULT_OG_IMAGE_PATH, OG_IMAGE_SIZE, ogLocale, robots } from '@/lib/seo/metadata';
import { SITE_NAME, siteUrl } from '@/lib/seo/site';
import { THEME_COOKIE } from '@/stores/ui-store';
import '@/styles/globals.css';

/**
 * Site-wide defaults. Indexable pages override title/description/canonical/OG through `pageMetadata`
 * (src/lib/seo/metadata.ts); everything else inherits `unlisted` robots, so a page has to opt into the index.
 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('meta');
  const locale = await getLocale();
  const verification = {
    ...(process.env.YANDEX_VERIFICATION ? { yandex: process.env.YANDEX_VERIFICATION } : {}),
    ...(process.env.GOOGLE_SITE_VERIFICATION
      ? { google: process.env.GOOGLE_SITE_VERIFICATION }
      : {}),
  };
  const image = { url: DEFAULT_OG_IMAGE_PATH, ...OG_IMAGE_SIZE, alt: t('title') };
  return {
    metadataBase: new URL(siteUrl()),
    title: { default: t('title'), template: `%s · ${SITE_NAME}` },
    description: t('description'),
    applicationName: SITE_NAME,
    robots: robots('unlisted'),
    manifest: '/manifest.webmanifest',
    icons: {
      icon: [
        { url: '/favicon.ico', sizes: '48x48' },
        { url: '/icons/icon.svg', type: 'image/svg+xml' },
      ],
      apple: '/icons/apple-touch-icon.png',
    },
    appleWebApp: { capable: true, title: SITE_NAME, statusBarStyle: 'default' },
    formatDetection: { telephone: false, email: false, address: false },
    openGraph: {
      type: 'website',
      siteName: SITE_NAME,
      locale: ogLocale(locale),
      title: t('title'),
      description: t('description'),
      images: [image],
    },
    twitter: {
      card: 'summary_large_image',
      title: t('title'),
      description: t('description'),
      images: [image],
    },
    ...(Object.keys(verification).length ? { verification } : {}),
  };
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#f8f9fc' },
    { media: '(prefers-color-scheme: dark)', color: '#0d101a' },
  ],
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const locale = await getLocale();
  const messages = await getMessages();
  const theme = (await cookies()).get(THEME_COOKIE)?.value;
  const explicitTheme = theme === 'light' || theme === 'dark' ? theme : undefined;
  return (
    <html lang={locale} data-theme={explicitTheme} suppressHydrationWarning>
      <body>
        <NextIntlClientProvider locale={locale} messages={messages}>
          <Providers>{children}</Providers>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
