import { describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import {
  ApiClient,
  buildQueryString,
  CLIENT_PAGE_HEADER,
  CLIENT_SESSION_HEADER,
  type SessionAccess,
} from './client';
import { ApiProblem, fieldErrorsOf, PROBLEM_CODES, REQUEST_ID_HEADER } from './problem';
import type { AuthResponse } from './schemas/auth';

const ME = {
  id: 'u1',
  email: 'a@b.c',
  firstName: 'A',
  lastName: 'B',
  avatarUrl: null,
  timezone: 'UTC',
  locale: 'ru',
  tenant: { id: 't1', slug: 's', name: 'S', branding: { logoUrl: null, primaryColor: null } },
  tenantRoles: [],
  telegramLinked: false,
} as const;

function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

function problem(status: number, code: string, extra: object = {}): Response {
  return json(
    { type: 'about:blank', title: `Title ${code}`, status, code, traceId: 'tr-1', ...extra },
    status,
    { 'Content-Type': 'application/problem+json' },
  );
}

function memorySession(
  initialToken: string | null = 'old',
): SessionAccess & { token: string | null; cleared: number } {
  const state = {
    token: initialToken,
    cleared: 0,
    getAccessToken: () => state.token,
    setSession: (auth: AuthResponse) => void (state.token = auth.accessToken),
    clearSession: () => {
      state.token = null;
      state.cleared += 1;
    },
  };
  return state;
}

const okSchema = z.object({ ok: z.boolean() });

describe('ApiClient', () => {
  it('sends bearer token, Accept-Language, Idempotency-Key and If-Match', async () => {
    const fetchImpl = vi.fn(async () => json({ ok: true }));
    const client = new ApiClient({
      session: memorySession('tok'),
      fetchImpl,
      getLocale: () => 'en',
    });
    await client.request('/items/1', {
      method: 'PATCH',
      body: { a: 1 },
      schema: okSchema,
      idempotencyKey: 'key-1',
      ifMatch: 7,
    });
    const [url, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit];
    const headers = new Headers(init.headers);
    expect(url).toBe('/api/v1/items/1');
    expect(headers.get('Authorization')).toBe('Bearer tok');
    expect(headers.get('Accept-Language')).toBe('en');
    expect(headers.get('Idempotency-Key')).toBe('key-1');
    expect(headers.get('If-Match')).toBe('"7"');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(init.credentials).toBe('include');
    expect(init.body).toBe('{"a":1}');
  });

  it('refreshes once for concurrent 401s (single-flight) and retries with the new token', async () => {
    const session = memorySession('expired');
    let refreshCalls = 0;
    const fetchImpl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/auth/refresh')) {
        refreshCalls += 1;
        await new Promise((resolve) => setTimeout(resolve, 10));
        return json({ accessToken: 'fresh', expiresIn: 900, user: ME });
      }
      const auth = new Headers(init?.headers).get('Authorization');
      return auth === 'Bearer fresh' ? json({ ok: true }) : problem(401, 'auth.invalid_token');
    });
    const client = new ApiClient({ session, fetchImpl });
    const results = await Promise.all([
      client.request('/a', { schema: okSchema }),
      client.request('/b', { schema: okSchema }),
      client.request('/c', { schema: okSchema }),
    ]);
    expect(results).toEqual([{ ok: true }, { ok: true }, { ok: true }]);
    expect(refreshCalls).toBe(1);
    expect(session.token).toBe('fresh');
  });

  it('clears the session and reports expiry when refresh is rejected', async () => {
    const session = memorySession('expired');
    const onSessionExpired = vi.fn();
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) =>
      String(input).endsWith('/auth/refresh')
        ? problem(401, PROBLEM_CODES.refreshInvalid)
        : problem(401, 'auth.invalid_token'),
    );
    const client = new ApiClient({ session, fetchImpl, onSessionExpired });
    await expect(client.request('/x')).rejects.toMatchObject({
      status: 401,
      code: 'auth.invalid_token',
    });
    expect(session.cleared).toBe(1);
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
  });

  it('keeps the session when refresh fails because of the network', async () => {
    const session = memorySession('expired');
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/auth/refresh')) throw new TypeError('Failed to fetch');
      return problem(401, 'auth.invalid_token');
    });
    const client = new ApiClient({ session, fetchImpl });
    await expect(client.request('/x')).rejects.toMatchObject({ code: PROBLEM_CODES.network });
    expect(session.cleared).toBe(0);
  });

  it('does not refresh for public endpoints', async () => {
    const fetchImpl = vi.fn(async () => problem(401, PROBLEM_CODES.invalidCredentials));
    const client = new ApiClient({ session: memorySession(null), fetchImpl });
    await expect(
      client.request('/auth/login', { method: 'POST', auth: false }),
    ).rejects.toBeInstanceOf(ApiProblem);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it('maps network failures to a transient client.network problem', async () => {
    const client = new ApiClient({
      session: memorySession(),
      fetchImpl: vi.fn(async () => Promise.reject(new TypeError('offline'))),
    });
    const error = await client.request('/x').catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).code).toBe(PROBLEM_CODES.network);
    expect((error as ApiProblem).isTransient).toBe(true);
  });

  it('parses RFC 9457 problems with field errors, args and Retry-After', async () => {
    const response = problem(429, PROBLEM_CODES.tooManyAttempts, {
      detail: 'Slow down',
      errors: [{ field: 'email', code: 'invalid_email', message: 'Bad email' }],
      args: { tenants: [{ slug: 'a', name: 'A' }] },
    });
    response.headers.set('Retry-After', '30');
    const client = new ApiClient({
      session: memorySession(),
      fetchImpl: vi.fn(async () => response),
    });
    const error = (await client.request('/x').catch((caught: unknown) => caught)) as ApiProblem;
    expect(error.status).toBe(429);
    expect(error.title).toBe(`Title ${PROBLEM_CODES.tooManyAttempts}`);
    expect(error.detail).toBe('Slow down');
    expect(error.retryAfterSec).toBe(30);
    expect(error.traceId).toBe('tr-1');
    expect(error.args.tenants).toEqual([{ slug: 'a', name: 'A' }]);
    expect(fieldErrorsOf(error)).toEqual({ email: 'Bad email' });
    expect(error.isTransient).toBe(true);
  });

  it('falls back gracefully for non-JSON error bodies', async () => {
    const client = new ApiClient({
      session: memorySession(),
      fetchImpl: vi.fn(
        async () =>
          new Response('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' }),
      ),
    });
    const error = (await client.request('/x').catch((caught: unknown) => caught)) as ApiProblem;
    expect(error.status).toBe(502);
    expect(error.code).toBe(PROBLEM_CODES.unknown);
    expect(error.title).toBe('Bad Gateway');
  });

  it('rejects responses that violate the contract schema', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const client = new ApiClient({
      session: memorySession(),
      fetchImpl: vi.fn(async () => json({ ok: 'yes' })),
    });
    await expect(client.request('/x', { schema: okSchema })).rejects.toMatchObject({
      code: PROBLEM_CODES.invalidResponse,
    });
  });

  it('sends the page and tab context for the activity log', async () => {
    const fetchImpl = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit) =>
      json({ ok: true }),
    );
    const client = new ApiClient({
      session: memorySession('tok'),
      fetchImpl,
      getClientContext: () => ({ page: '/courses/c1', sessionId: 'tab-12345678' }),
    });
    await client.request('/x', { schema: okSchema });
    const headers = new Headers(fetchImpl.mock.calls[0]?.[1]?.headers);
    expect(headers.get(CLIENT_PAGE_HEADER)).toBe('/courses/c1');
    expect(headers.get(CLIENT_SESSION_HEADER)).toBe('tab-12345678');
  });

  it('exposes the request id of failures and reports contract mismatches', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const onContractMismatch = vi.fn();
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(problem(500, 'internal.error', { requestId: 'req-1' }))
      .mockResolvedValueOnce(json({ ok: 'yes' }, 200, { [REQUEST_ID_HEADER]: 'req-2' }));
    const client = new ApiClient({ session: memorySession(), fetchImpl, onContractMismatch });

    const failure = (await client.request('/x').catch((caught: unknown) => caught)) as ApiProblem;
    expect(failure.requestId).toBe('req-1');
    await expect(client.request('/x', { schema: okSchema })).rejects.toMatchObject({
      requestId: 'req-2',
    });
    expect(onContractMismatch).toHaveBeenCalledWith(
      expect.objectContaining({ code: PROBLEM_CODES.invalidResponse }),
    );
  });

  it('returns undefined for 204 responses', async () => {
    const client = new ApiClient({
      session: memorySession(),
      fetchImpl: vi.fn(async () => new Response(null, { status: 204 })),
    });
    await expect(client.request('/x', { method: 'DELETE' })).resolves.toBeUndefined();
  });
});

describe('buildQueryString', () => {
  it('skips empty values and repeats arrays', () => {
    expect(
      buildQueryString({
        q: 'math',
        cursor: null,
        empty: '',
        limit: 25,
        tag: ['a', 'b'],
        mine: false,
      }),
    ).toBe('?q=math&limit=25&tag=a&tag=b&mine=false');
    expect(buildQueryString({})).toBe('');
  });
});
