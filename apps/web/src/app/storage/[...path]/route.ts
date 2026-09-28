import type { NextRequest } from 'next/server';
import { proxyToCoreApi } from '@/lib/server/core-api-proxy';

/**
 * Files of core-api's local storage (STORAGE_DRIVER=local): signed upload/download links and public HLS,
 * `/storage/{key}` → `${CORE_API_URL}/storage/{key}`. In production nginx routes `/storage/` straight to
 * core-api (large uploads must not pass through Node), so this handler is the fallback for dev and for a
 * deployment without that nginx location.
 */
export const dynamic = 'force-dynamic';

const STORAGE_PREFIX = '/storage/';

type Context = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: Context): Promise<Response> {
  const { path } = await context.params;
  return proxyToCoreApi(request, STORAGE_PREFIX, path);
}

export { proxy as GET, proxy as HEAD, proxy as PUT };
