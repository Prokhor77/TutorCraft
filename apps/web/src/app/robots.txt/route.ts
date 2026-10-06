import { absoluteUrl, indexingAllowed } from '@/lib/seo/site';

export const dynamic = 'force-dynamic';

/**
 * robots.txt as a Route Handler rather than `app/robots.ts`: the metadata API has no field for Yandex
 * `Clean-param`, which folds UTM-tagged duplicates into one URL.
 *
 * Only the JSON API is disallowed. App, auth and invite pages are deliberately NOT listed: they carry
 * `noindex` (src/lib/seo/metadata.ts), and a crawler blocked by robots.txt never fetches the page, never sees
 * that `noindex` and may still index the bare URL from a link (the footer links to /home, /grades…).
 * User files get `X-Robots-Tag` instead (next.config.ts, nginx). Neither is security — auth is.
 */
const DISALLOW = ['/api/'];
const CLEAN_PARAMS = [
  'utm_source',
  'utm_medium',
  'utm_campaign',
  'utm_term',
  'utm_content',
  'yclid',
  'gclid',
  'fbclid',
  'ymclid',
  '_openstat',
  'from',
];

export function GET(): Response {
  const lines = indexingAllowed()
    ? [
        'User-agent: *',
        'Allow: /',
        ...DISALLOW.map((path) => `Disallow: ${path}`),
        '',
        'User-agent: Yandex',
        'Allow: /',
        ...DISALLOW.map((path) => `Disallow: ${path}`),
        `Clean-param: ${CLEAN_PARAMS.join('&')}`,
        '',
        `Sitemap: ${absoluteUrl('/sitemap.xml')}`,
      ]
    : // A deployment without a real domain (bare IP, staging) must stay out of the index entirely.
      ['User-agent: *', 'Disallow: /'];
  return new Response(`${lines.join('\n')}\n`, {
    headers: {
      'Content-Type': 'text/plain; charset=utf-8',
      'Cache-Control': 'public, max-age=3600',
    },
  });
}
