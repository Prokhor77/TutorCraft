import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

const wsUrl = process.env.NEXT_PUBLIC_WS_URL ?? '';

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
    'connect-src': [
      "'self'",
      'https:',
      'http:',
      ...(wsOrigin ? [wsOrigin.replace(/^http/, 'ws')] : []),
      'ws:',
      'wss:',
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
  // `/api/v1/*` is proxied to CORE_API_URL by src/app/api/v1/[...path]/route.ts (runtime config;
  // `rewrites()` would freeze the destination at build time in standalone output).
  async headers() {
    return [
      { source: '/:path*', headers: securityHeaders },
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
