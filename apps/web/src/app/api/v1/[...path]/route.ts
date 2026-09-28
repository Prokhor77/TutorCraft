import type { NextRequest } from 'next/server';
import { proxyToCoreApi } from '@/lib/server/core-api-proxy';

/**
 * Same-origin BFF proxy `/api/v1/*` → `${CORE_API_URL}/api/v1/*` (ARCH, ADR-003: refresh cookie is
 * same-origin, Path=/api/v1/auth). Implemented as a Route Handler instead of `next.config` rewrites
 * because rewrites are frozen at build time in `output: 'standalone'`, while CORE_API_URL is runtime config.
 */
export const dynamic = 'force-dynamic';

const API_PREFIX = '/api/v1/';

type Context = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: Context): Promise<Response> {
  const { path } = await context.params;
  return proxyToCoreApi(request, API_PREFIX, path);
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
