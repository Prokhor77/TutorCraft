import type { NextRequest } from 'next/server';
import { coreApiUrl } from '@/lib/server/core-api';

/**
 * Same-origin BFF proxy `/api/v1/*` → `${CORE_API_URL}/api/v1/*` (ARCH, ADR-003: refresh cookie is
 * same-origin, Path=/api/v1/auth). Implemented as a Route Handler instead of `next.config` rewrites
 * because rewrites are frozen at build time in `output: 'standalone'`, while CORE_API_URL is runtime config.
 */
export const dynamic = 'force-dynamic';

const HOP_BY_HOP_HEADERS = [
  'host',
  'connection',
  'keep-alive',
  'transfer-encoding',
  'upgrade',
  'proxy-connection',
  'te',
  'trailer',
];
/** fetch() transparently decompresses, so upstream encoding/length no longer describe the body. */
const STALE_RESPONSE_HEADERS = [
  'content-encoding',
  'content-length',
  'transfer-encoding',
  'connection',
];
const BAD_GATEWAY = 502;
const METHODS_WITHOUT_BODY = new Set(['GET', 'HEAD']);

type Context = { params: Promise<{ path: string[] }> };

function forwardHeaders(request: NextRequest): Headers {
  const headers = new Headers(request.headers);
  HOP_BY_HOP_HEADERS.forEach((name) => headers.delete(name));
  headers.set('x-forwarded-host', request.headers.get('host') ?? '');
  headers.set('x-forwarded-proto', request.nextUrl.protocol.replace(':', ''));
  return headers;
}

function badGateway(): Response {
  const problem = {
    type: 'about:blank',
    title: 'Core API is unavailable',
    status: BAD_GATEWAY,
    code: 'gateway.unavailable',
    traceId: '',
  };
  return Response.json(problem, {
    status: BAD_GATEWAY,
    headers: { 'content-type': 'application/problem+json' },
  });
}

async function proxy(request: NextRequest, context: Context): Promise<Response> {
  const { path } = await context.params;
  const target = `${coreApiUrl()}/api/v1/${path.map(encodeURIComponent).join('/')}${request.nextUrl.search}`;
  const init: RequestInit & { duplex?: 'half' } = {
    method: request.method,
    headers: forwardHeaders(request),
    body: METHODS_WITHOUT_BODY.has(request.method) ? undefined : request.body,
    duplex: 'half',
    redirect: 'manual',
    cache: 'no-store',
  };
  let upstream: Response;
  try {
    upstream = await fetch(target, init);
  } catch (error) {
    console.error(
      '[proxy] core-api unreachable',
      error instanceof Error ? error.message : 'unknown',
    );
    return badGateway();
  }
  const headers = new Headers(upstream.headers);
  STALE_RESPONSE_HEADERS.forEach((name) => headers.delete(name));
  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers,
  });
}

export {
  proxy as GET,
  proxy as POST,
  proxy as PUT,
  proxy as PATCH,
  proxy as DELETE,
  proxy as HEAD,
  proxy as OPTIONS,
};
