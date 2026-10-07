import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

const wsUrl = process.env.NEXT_PUBLIC_WS_URL ?? '';
/**
 * Origin of the S3 gateway the browser talks to directly (pre-signed PUT/GET, HLS chunks).
 * Needed in `connect-src`: uploads bypass the app and go to another origin, so a strict CSP
 * would block them. Empty in dev, where S3 is same-host on :9000 and covered by the dev branch.
 */
const s3Origin = process.env.NEXT_PUBLIC_S3_ORIGIN ?? '';

/** Security headers (NFR-SEC-03). Scripts: self + Google Identity + Telegram widget; no framing of the app. */
function buildContentSecurityPolicy(): string {
  const isDev = process.env.NODE_ENV !== 'production';
  const wsOrigin = wsUrl ? new URL(wsUrl).origin : '';
  const directives: Record<string, string[]> = {
    'default-src': ["'self'"],
    // Next.js App Router still injects inline bootstrap scripts; 'unsafe-eval' only for dev HMR.
    'script-src': [
      "'self'",
      "'unsafe-inline'",
      'https://accounts.google.com/gsi/client',
      'https://telegram.org',
      ...(isDev ? ["'unsafe-eval'"] : []),
    ],
    'style-src': ["'self'", "'unsafe-inline'", 'https://accounts.google.com/gsi/style'],
    'img-src': ["'self'", 'data:', 'blob:', 'https:', 'http:'],
    'media-src': ["'self'", 'blob:', 'https:', 'http:'],
    'font-src': ["'self'", 'data:'],
    // Narrow on purpose: the app only ever calls its own `/api/v1/*` BFF, the notifier socket
    // and the S3 gateway. Allowing `https: http:` here would let an injected script POST the
    // whole session anywhere. In dev the hosts move around, so keep it loose there only.
    'connect-src': isDev
      ? ["'self'", 'http:', 'https:', 'ws:', 'wss:']
      : [
          "'self'",
          ...(wsOrigin ? [wsOrigin.replace(/^http/, 'ws')] : ['ws:', 'wss:']),
          ...(s3Origin ? [s3Origin] : []),
        ],
    'frame-src': ["'self'", 'https:', 'http:'],
    'worker-src': ["'self'", 'blob:'],
    'frame-ancestors': ["'none'"],
    'base-uri': ["'self'"],
    'form-action': ["'self'"],
    'object-src': ["'self'"],
  };
  return Object.entries(directives)
    .map(([name, values]) => `${name} ${values.join(' ')}`)
    .join('; ');
}

const securityHeaders = [
  { key: 'Content-Security-Policy', value: buildContentSecurityPolicy() },
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  { key: 'X-Frame-Options', value: 'DENY' },
  {
    key: 'Permissions-Policy',
    value: 'camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()',
  },
];

const nextConfig: NextConfig = {
  output: 'standalone',
  reactStrictMode: true,
  poweredByHeader: false,
  // No `.map` files next to the bundles: with them, minified client code de-minifies back into
  // readable sources (component names, comments, internal route helpers) in any browser devtools.
  // This is the only "obfuscation" that means anything here — server code never leaves the host.
  productionBrowserSourceMaps: false,
  // `opengraph-image` routes read their TTF fonts with fs at runtime (src/lib/seo/og-image.tsx);
  // without this the standalone output would not copy them next to server.js.
  outputFileTracingIncludes: { '/**/*': ['./src/assets/og/*.ttf'] },
  compiler: {
    // Strip console.* from the client bundle; console.error stays so real failures are still
    // visible in production. Removes stray debug output that leaks internal state and ids.
    removeConsole: process.env.NODE_ENV === 'production' ? { exclude: ['error'] } : false,
  },
  // `/api/v1/*` is proxied to CORE_API_URL by src/app/api/v1/[...path]/route.ts (runtime config;
  // `rewrites()` would freeze the destination at build time in standalone output).
  async headers() {
    return [
      { source: '/:path*', headers: securityHeaders },
      // API JSON and user files must never show up in search results. A header (not a robots.txt
      // Disallow) is what keeps a leaked URL out of the index: a blocked crawler never sees noindex.
      { source: '/api/:path*', headers: [{ key: 'X-Robots-Tag', value: 'noindex, nofollow' }] },
      {
        source: '/storage/:path*',
        headers: [{ key: 'X-Robots-Tag', value: 'noindex, nofollow, noimageindex' }],
      },
      {
        source: '/sw.js',
        headers: [
          { key: 'Cache-Control', value: 'no-cache' },
          { key: 'Service-Worker-Allowed', value: '/' },
        ],
      },
    ];
  },
};

export default withNextIntl(nextConfig);
