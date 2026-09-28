import 'server-only';
import type { NextRequest } from 'next/server';
import { coreApiUrl } from '@/lib/server/core-api';

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

/**
 * Streams the request to `${CORE_API_URL}${prefix}${path}` and the response back (bodies are never buffered).
 * `prefix` is an absolute path ending with `/`, e.g. `/api/v1/`.
 */
export async function proxyToCoreApi(
  request: NextRequest,
  prefix: string,
  path: string[],
): Promise<Response> {
  const target = `${coreApiUrl()}${prefix}${path.map(encodeURIComponent).join('/')}${request.nextUrl.search}`;
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
